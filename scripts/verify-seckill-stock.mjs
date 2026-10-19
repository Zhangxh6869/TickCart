#!/usr/bin/env node
/**
 * B2 并发验证：秒杀库存原子扣减（不超卖、不超限购）。
 *
 * 前置：
 *   1. 后端启动时关掉限流与登录频控（否则 429 会污染结果）：
 *        RATE_LIMIT_ENABLED=false LOGIN_GUARD_ENABLED=false mvn spring-boot:run
 *   2. 库里有 N 个测试账号 sku001..skuNNN（脚本不建账号，见 README 的验证命令）
 *
 * 用法：
 *   STOCK=50 USERS=120 node scripts/verify-seckill-stock.mjs
 *
 * 断言：
 *   ① 120 个不同用户并发抢 50 件库存 → 恰好 50 成功，其余「已抢光」
 *   ② 同一用户限购 1 件，买成功后再次抢购 → 「每人限购 1 件」
 *   ③ 活动结束后抢购 → 「活动已结束」
 *
 * 注意：B2 阶段还没有 MQ 消费者，抢购成功的消息会堆在 trade.seckill.order 队列里，
 * 跑完记得清队列（脚本会提示）。
 */
const BASE = (process.env.BASE ?? 'http://localhost:8080').replace(/\/$/, '')
const ADMIN_USER = process.env.ADMIN_USER ?? 'admin'
const ADMIN_PASS = process.env.ADMIN_PASS ?? 'admin123'
const USER_PASS = process.env.TICKCART_PASS ?? '123456'
const STOCK = Number(process.env.STOCK ?? 50)
const USERS = Number(process.env.USERS ?? 120)
const PRODUCT_ID = Number(process.env.PRODUCT_ID ?? 302)
const SKU_ID = Number(process.env.SKU_ID ?? 490)
const ADDRESS_ID = Number(process.env.ADDRESS_ID ?? 124)

let adminToken = ''
let activityId = 0

async function call(method, path, body, token, extraHeaders = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...extraHeaders,
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
  return { status: res.status, body: json }
}

/**
 * 生成后端要的 LocalDateTime 字符串（本地时区，不带 Z）。
 *
 * ⚠ 不能用 toISOString()：它转成 UTC，北京时间会差 8 小时，
 * 后端按本地时间解析就会把「刚建的活动」判成「已结束」。
 */
function localDateTime(offsetMs) {
  const d = new Date(Date.now() + offsetMs)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    + `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function ok(msg) {
  console.log(`  ✓ ${msg}`)
}

function fail(msg) {
  console.log(`  ✗ ${msg}`)
  process.exitCode = 1
}

async function login(username, password) {
  const r = await call('POST', '/api/auth/login', { username, password })
  if (r.status !== 200 || r.body?.code !== 0) {
    throw new Error(`登录失败 ${username}: HTTP ${r.status} ${JSON.stringify(r.body)}`)
  }
  return r.body.data.accessToken
}

async function main() {
  console.log(`\n=== B2 秒杀库存并发验证 @ ${BASE} ===`)
  console.log(`库存 ${STOCK}，并发用户 ${USERS}\n`)

  adminToken = await login(ADMIN_USER, ADMIN_PASS)
  ok('管理员登录成功')

  // 建一场「进行中」的活动
  const start = localDateTime(-5 * 60 * 1000)
  const end = localDateTime(2 * 60 * 60 * 1000)
  const created = await call('POST', '/api/admin/seckill/activities', {
    title: '并发验证活动',
    productId: PRODUCT_ID,
    skuId: SKU_ID,
    seckillPrice: 9.9,
    seckillStock: STOCK,
    limitPerUser: 1,
    startTime: start,
    endTime: end,
  }, adminToken)
  if (created.status !== 200 || created.body?.code !== 0) {
    throw new Error(`建活动失败：HTTP ${created.status} ${JSON.stringify(created.body)}`)
  }
  activityId = created.body.data
  ok(`活动已创建 id=${activityId}（库存 ${STOCK}，限购 1）`)

  // ① 多用户并发抢购
  console.log(`\n① ${USERS} 个不同用户并发抢 ${STOCK} 件`)
  const usernames = Array.from({ length: USERS }, (_, i) => `sku${String(i + 1).padStart(3, '0')}`)
  const tokens = await Promise.all(usernames.map((u) => login(u, USER_PASS)))
  ok(`${tokens.length} 个测试账号登录完成`)

  const results = await Promise.all(
    tokens.map((token) =>
      call('POST', `/api/seckill/activities/${activityId}/order`, { quantity: 1 }, token),
    ),
  )
  const success = results.filter((r) => r.status === 200 && r.body?.code === 0)
  const soldOut = results.filter((r) => r.status === 409 && /抢光/.test(r.body?.message ?? ''))
  const others = results.filter(
    (r) => !(r.status === 200 && r.body?.code === 0) && !(r.status === 409 && /抢光/.test(r.body?.message ?? '')),
  )

  if (success.length === STOCK) {
    ok(`${STOCK} 个成功 —— 与库存完全一致，未超卖`)
  } else {
    fail(`期望 ${STOCK} 个成功，实际 ${success.length} 个`)
  }
  if (soldOut.length === USERS - STOCK) {
    ok(`${soldOut.length} 个「已抢光」`)
  } else {
    fail(`期望 ${USERS - STOCK} 个「已抢光」，实际 ${soldOut.length} 个`)
  }
  if (others.length > 0) {
    const sample = others[0]
    fail(`出现 ${others.length} 个异常响应，样例：HTTP ${sample.status} ${JSON.stringify(sample.body)}`)
  }

  // ② 限购：挑一个「已经抢到」的用户再抢一次（等请求锁 3 秒过期）
  console.log('\n② 限购校验（同一用户再抢一次）')
  const winnerIndex = results.findIndex((r) => r.status === 200 && r.body?.code === 0)
  if (winnerIndex < 0) {
    fail('没有成功用户，无法验证限购')
    return
  }
  await new Promise((r) => setTimeout(r, 3200))
  const again = await call('POST', `/api/seckill/activities/${activityId}/order`,
    { quantity: 1 }, tokens[winnerIndex])
  if (again.status === 409 && /限购/.test(again.body?.message ?? '')) {
    ok(`拒绝重复购买：${again.body.message}`)
  } else {
    fail(`期望「每人限购 1 件」，实际 HTTP ${again.status} ${JSON.stringify(again.body)}`)
  }

  // ③ 结束后的抢购
  console.log('\n③ 活动结束后抢购')
  await call('PUT', `/api/admin/seckill/activities/${activityId}`, {
    title: '并发验证活动',
    productId: PRODUCT_ID,
    skuId: SKU_ID,
    seckillPrice: 9.9,
    seckillStock: STOCK,
    limitPerUser: 1,
    startTime: start,
    endTime: localDateTime(-60 * 1000),
  }, adminToken)
  const ended = await call('POST', `/api/seckill/activities/${activityId}/order`,
    { quantity: 1 }, tokens[1])
  if (ended.status === 409) {
    ok(`拒绝：${ended.body.message}`)
  } else {
    fail(`期望 409「活动已结束」，实际 HTTP ${ended.status} ${JSON.stringify(ended.body)}`)
  }

  // 清理提示
  console.log(`\n=== 清理 ===`)
  console.log(`  活动 id=${activityId}（sold_count 为 0，可直接删除）`)
  console.log(`  DELETE FROM seckill_activity WHERE id=${activityId};`)
  console.log(`  Redis：redis-cli --scan --pattern 'seckill:*' | xargs -r redis-cli del`)
  console.log(`  MQ 队列：rabbitmqctl purge_queue trade.seckill.order`)
  console.log(`  测试账号：DELETE FROM sys_user WHERE username LIKE 'sku%';`)
  console.log(`  地址参数：addressId=${ADDRESS_ID}（脚本未下单，仅占位）\n`)
}

main().catch((e) => {
  console.error(`\n验证脚本异常：${e.message}\n`)
  process.exitCode = 1
})
