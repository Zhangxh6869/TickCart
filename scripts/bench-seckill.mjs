#!/usr/bin/env node
/**
 * 秒杀压测：出 QPS / P95 / 成功率 / 是否超卖，可对照普通下单。
 *
 * 前置：
 *   1. 后端关限流与登录频控（否则秒杀接口 5/秒 直接把压测卡死）：
 *        RATE_LIMIT_ENABLED=false LOGIN_GUARD_ENABLED=false \
 *        MYSQL_PORT=3307 SERVER_PORT=8080 mvn spring-boot:run
 *   2. 账号已准备好：
 *        node scripts/seed-bench-users.mjs 500
 *
 * 场景：
 *   SCENARIO=seckill STOCK=100 node scripts/bench-seckill.mjs   # 库存 100，N 个用户并发抢
 *   SCENARIO=seckill STOCK=1000 ...                             # 库存 = 用户数，测全成功吞吐
 *   SCENARIO=normal  node scripts/bench-seckill.mjs             # 对照：普通立即购买（同步建单）
 *   SCENARIO=single  node scripts/bench-seckill.mjs             # 单用户 50 并发（限购 + 请求锁）
 *
 * 削峰对照（S4）：加 SECKILL_CONSUMER_ENABLED=false 重启后端再跑同一场景，
 *   抢购延迟与成功率应基本不变，而 MQ 队列积压等于成功数。
 */
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const REPO = path.resolve(HERE, '..')
const TOKEN_FILE = path.join(REPO, '.workbuddy-ai', 'tmp', 'bench-tokens.json')

const BASE = (process.env.BASE ?? 'http://localhost:8080').replace(/\/$/, '')
const ADMIN_USER = process.env.ADMIN_USER ?? 'admin'
const ADMIN_PASS = process.env.ADMIN_PASS ?? 'admin123'
const SCENARIO = process.env.SCENARIO ?? 'seckill'
const STOCK = Number(process.env.STOCK ?? 100)
const LIMIT = Number(process.env.LIMIT ?? 1)
const USERS = Number(process.env.USERS ?? 0) // 0 = 用缓存里的全部账号
const CLEANUP = process.env.CLEANUP === '1'
const PRODUCT_ID = Number(process.env.PRODUCT_ID ?? 302)
const SKU_ID = Number(process.env.SKU_ID ?? 490)
const DRAIN_TIMEOUT_MS = Number(process.env.DRAIN_TIMEOUT_MS ?? 60000)

let adminToken = ''
let activityId = 0

function log(msg = '') {
  console.log(msg)
}

function redisCli(...args) {
  return execFileSync('docker', ['exec', '-i', 'tickcart-redis', 'redis-cli', ...args], {
    encoding: 'utf8',
  }).trim()
}

function rmd(...args) {
  return execFileSync('docker', ['exec', '-i', 'tickcart-rabbitmq', 'rabbitmqctl', ...args], {
    encoding: 'utf8',
  })
}

function queueDepth(queue) {
  const out = rmd('list_queues', 'name', 'messages')
  // 按整名匹配：startsWith 会先撞上 trade.seckill.order.retry，把深度读成 0
  const line = out.split('\n').find((l) => l.trim().split(/\s+/)[0] === queue)
  return line ? Number(line.trim().split(/\s+/)[1]) : 0
}

async function login(username, password) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const json = await res.json()
  if (json.code !== 0) throw new Error(`登录失败：${JSON.stringify(json)}`)
  return json.data.accessToken
}

async function call(method, url, body, token) {
  const started = Date.now()
  const res = await fetch(BASE + url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = { raw: text }
  }
  return { status: res.status, code: json?.code, message: json?.message, data: json?.data, ms: Date.now() - started }
}

/** 本地时间（不能 toISOString：那是 UTC，北京时间差 8 小时） */
function localDateTime(offsetMs) {
  const d = new Date(Date.now() + offsetMs)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    + `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)
  return sorted[idx]
}

function report(title, results, extra = {}) {
  const ok = results.filter((r) => r.status === 200 && r.code === 0)
  const limited = results.filter((r) => r.status === 429)
  const soldOut = results.filter((r) => r.status === 409 && /抢光/.test(r.message ?? ''))
  const overLimit = results.filter((r) => r.status === 409 && /限购/.test(r.message ?? ''))
  const notReady = results.filter((r) => r.status === 503)
  const others = results.filter(
    (r) => !(r.status === 200 && r.code === 0)
      && r.status !== 429 && r.status !== 503
      && !(r.status === 409 && (/抢光/.test(r.message ?? '') || /限购/.test(r.message ?? ''))),
  )

  const latencies = ok.map((r) => r.ms).sort((a, b) => a - b)
  const wallMs = extra.wallMs ?? 0
  const qps = wallMs > 0 ? Math.round((results.length / wallMs) * 1000) : 0

  log('')
  log(`--- ${title} ---`)
  log(`  请求 ${results.length} · 成功 ${ok.length} · 429 限流 ${limited.length} · 409 已抢光 ${soldOut.length}`
    + ` · 409 超限购 ${overLimit.length} · 503 未就绪 ${notReady.length} · 其他 ${others.length}`)
  log(`  墙钟 ${wallMs}ms · QPS ${qps}`)
  if (latencies.length > 0) {
    log(`  成功延迟 P50 ${percentile(latencies, 50)}ms · P95 ${percentile(latencies, 95)}ms`
      + ` · P99 ${percentile(latencies, 99)}ms · max ${latencies[latencies.length - 1]}ms`)
  }
  if (others.length > 0) {
    log(`  ⚠ 其他响应样例：HTTP ${others[0].status} ${JSON.stringify(others[0].message ?? '')}`)
  }
  return { ok, limited, soldOut, overLimit, notReady, others, latencies, qps, wallMs }
}

async function waitSoldCount(target, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  let last = -1
  while (Date.now() < deadline) {
    const detail = await call('GET', `/api/admin/seckill/activities/${activityId}`, undefined, adminToken)
    last = detail.data?.soldCount ?? -1
    if (last >= target) return last
    await new Promise((r) => setTimeout(r, 500))
  }
  return last
}

async function main() {
  if (!fs.existsSync(TOKEN_FILE)) {
    console.error(`token 缓存不存在：${TOKEN_FILE}\n先跑 node scripts/seed-bench-users.mjs <N>`)
    process.exit(1)
  }
  const cache = JSON.parse(fs.readFileSync(TOKEN_FILE, 'utf8'))
  const users = USERS > 0 ? cache.entries.slice(0, USERS) : cache.entries
  if (users.length === 0) {
    console.error('token 缓存里没有账号')
    process.exit(1)
  }

  adminToken = await login(ADMIN_USER, ADMIN_PASS)
  log(`\n=== 场景 ${SCENARIO} · 账号 ${users.length} · BASE ${BASE} ===`)

  // ---------------- 单用户连点 ----------------
  if (SCENARIO === 'single') {
    const stock = Number(process.env.SINGLE_STOCK ?? 10)
    const created = await call('POST', '/api/admin/seckill/activities', {
      title: '压测-单用户连点',
      productId: PRODUCT_ID,
      skuId: SKU_ID,
      seckillPrice: 9.9,
      seckillStock: stock,
      limitPerUser: LIMIT,
      startTime: localDateTime(-5 * 60 * 1000),
      endTime: localDateTime(2 * 60 * 60 * 1000),
    }, adminToken)
    activityId = created.data
    log(`活动 id=${activityId}（库存 ${stock}，限购 ${LIMIT}）`)

    const single = users[0]
    const results = await Promise.all(
      Array.from({ length: 50 }, () =>
        call('POST', `/api/seckill/activities/${activityId}/order`,
          { quantity: 1, addressId: single.addressId }, single.token)),
    )
    report(`单用户 50 并发（限购 ${LIMIT}）`, results)
    const sold = await waitSoldCount(LIMIT, 10000)
    log(`  sold_count=${sold}（应收敛到 ${LIMIT}）`)
    await finish()
    return
  }

  // ---------------- 普通下单对照 ----------------
  if (SCENARIO === 'normal') {
    const n = Math.min(users.length, STOCK)
    log(`普通立即购买 ${n} 并发（同步建单 + 扣库存）`)
    const started = Date.now()
    const results = await Promise.all(
      users.slice(0, n).map((u) =>
        call('POST', '/api/orders/buy-now', {
          addressId: u.addressId,
          productId: PRODUCT_ID,
          skuId: SKU_ID,
          quantity: 1,
        }, u.token)),
    )
    report('普通立即购买', results, { wallMs: Date.now() - started })
    await finish(true)
    return
  }

  // ---------------- 秒杀（默认） ----------------
  const created = await call('POST', '/api/admin/seckill/activities', {
    title: '压测-秒杀',
    productId: PRODUCT_ID,
    skuId: SKU_ID,
    seckillPrice: 9.9,
    seckillStock: STOCK,
    limitPerUser: LIMIT,
    startTime: localDateTime(-5 * 60 * 1000),
    endTime: localDateTime(2 * 60 * 60 * 1000),
  }, adminToken)
  if (created.code !== 0) throw new Error(`建活动失败：${JSON.stringify(created)}`)
  activityId = created.data
  log(`活动 id=${activityId}（库存 ${STOCK}，限购 ${LIMIT}，用户 ${users.length}）`)

  const started = Date.now()
  const results = await Promise.all(
    users.map((u) =>
      call('POST', `/api/seckill/activities/${activityId}/order`,
        { quantity: 1, addressId: u.addressId }, u.token)),
  )
  const stats = report(`秒杀抢购（${users.length} 并发抢 ${STOCK} 件）`, results, {
    wallMs: Date.now() - started,
  })

  // ---------------- 一致性校验 ----------------
  log('')
  log('--- 一致性校验 ---')
  const oversell = Math.max(0, stats.ok.length - STOCK)
  const redisRemaining = Number(redisCli('get', `seckill:stock:${activityId}`) ?? 'NaN')
  const sold = await waitSoldCount(stats.ok.length, DRAIN_TIMEOUT_MS)
  const queue = queueDepth('trade.seckill.order')

  const expectDrain = process.env.EXPECT_DRAIN !== '0'
  log(`  成功数 ${stats.ok.length} · 库存 ${STOCK} · 超卖 ${oversell}`)
  log(`  Redis 剩余 ${Number.isNaN(redisRemaining) ? '(读取失败)' : redisRemaining}`
    + ` · sold_count ${sold} · 队列积压 ${queue}`)

  if (oversell === 0) log('  ✓ 未超卖')
  else {
    log('  ✗ 超卖！')
    process.exitCode = 1
  }
  if (expectDrain) {
    if (sold === stats.ok.length) log('  ✓ 账目一致（DB == 成功数）')
    else {
      log(`  ✗ 账目不一致：sold_count(${sold}) != 成功数(${stats.ok.length})`)
      process.exitCode = 1
    }
  } else {
    // S4 场景：消费者停着，成功数应该原样堆在队列里
    if (queue === stats.ok.length) log(`  ✓ 消息全部堆在队列（${queue} 条）—— 抢购不受建单速度影响`)
    else log(`  ✗ 队列积压(${queue}) != 成功数(${stats.ok.length})`)
  }

  await finish()
}

async function finish(skipActivity = false) {
  if (CLEANUP) {
    log('')
    log('清理：删除 SK 订单、幂等记录与测试活动')
    execFileSync('docker', ['exec', '-i', 'tickcart-mysql', 'mysql', '-uroot', '-proot'], {
      // 同时清两种来源：秒杀订单（SK 前缀）与普通下单（bench 账号下的 NO 订单）。
      // 子表只能按 order_no 关联（order_item/payment_record 没有 user_id 列）
      input: `START TRANSACTION;
DELETE FROM tickcart.order_item        WHERE order_no IN (
  SELECT order_no FROM tickcart.t_order
  WHERE order_no LIKE 'SK%'
     OR user_id IN (SELECT id FROM tickcart.sys_user WHERE username LIKE 'bench%'));
DELETE FROM tickcart.payment_record    WHERE order_no IN (
  SELECT order_no FROM tickcart.t_order
  WHERE order_no LIKE 'SK%'
     OR user_id IN (SELECT id FROM tickcart.sys_user WHERE username LIKE 'bench%'));
DELETE FROM tickcart.t_order           WHERE order_no LIKE 'SK%'
     OR user_id IN (SELECT id FROM tickcart.sys_user WHERE username LIKE 'bench%');
DELETE FROM tickcart.idempotent_record WHERE scene = 'seckill.buy';
DELETE FROM tickcart.seckill_activity;
COMMIT;`,
      stdio: ['pipe', 'ignore', 'inherit'],
    })
    for (const key of redisCli('--scan', '--pattern', 'seckill:*').split('\n').filter(Boolean)) {
      redisCli('del', key)
    }
    // 队列默认不自动清：S4 就是要看积压，清理由脚本打印命令后人工执行
    log(`  当前 trade.seckill.order 积压：${queueDepth('trade.seckill.order')}`
      + `（S4 场景需要 rabbitmqctl purge_queue trade.seckill.order 手动清）`)
    log('  已清理（账号保留，token 可复用）')
  } else {
    log('')
    log('清理提示：CLEANUP=1 可让脚本自动清理订单与活动')
    if (!skipActivity) log(`  活动 id=${activityId}`)
  }
}

main().catch((e) => {
  console.error(`\n压测脚本异常：${e.message}\n`)
  process.exitCode = 1
})
