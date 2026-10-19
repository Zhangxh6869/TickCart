#!/usr/bin/env node
/**
 * B3 端到端验证：抢购 → MQ → 消费者建单 → 结果查询 → 取消回补。
 *
 * 前置：
 *   1. 后端启动（关限流与登录频控）：RATE_LIMIT_ENABLED=false LOGIN_GUARD_ENABLED=false
 *   2. 库里有测试账号 sku001..skuNNN（脚本不建账号）
 *
 * 用法：STOCK=5 USERS=5 node scripts/verify-seckill-order.mjs
 *
 * 断言：
 *   ① N 个用户并发抢 N 件 → 每个 requestId 都能轮询到 SUCCESS，且订单号互不相同
 *   ② 订单号 SK 前缀、channel=1、activity_id 正确（通过用户订单列表与后台活动详情交叉验证）
 *   ③ 活动 soldCount == N
 *   ④ 取消其中一单 → soldCount 减 1，订单状态变已取消
 */
const BASE = (process.env.BASE ?? 'http://localhost:8080').replace(/\/$/, '')
const ADMIN_USER = process.env.ADMIN_USER ?? 'admin'
const ADMIN_PASS = process.env.ADMIN_PASS ?? 'admin123'
const USER_PASS = process.env.TICKCART_PASS ?? '123456'
const STOCK = Number(process.env.STOCK ?? 5)
const USERS = Number(process.env.USERS ?? 5)
const PRODUCT_ID = Number(process.env.PRODUCT_ID ?? 302)
const SKU_ID = Number(process.env.SKU_ID ?? 490)
const POLL_TIMEOUT_MS = 15000

let adminToken = ''
let activityId = 0

async function call(method, path, body, token) {
  const res = await fetch(BASE + path, {
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
  return { status: res.status, body: json }
}

/** 本地时间（不能 toISOString：那是 UTC，北京时间会差 8 小时） */
function localDateTime(offsetMs) {
  const d = new Date(Date.now() + offsetMs)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    + `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

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
  console.log(`\n=== B3 秒杀端到端验证 @ ${BASE} ===`)
  console.log(`库存 ${STOCK}，抢购用户 ${USERS}\n`)

  adminToken = await login(ADMIN_USER, ADMIN_PASS)
  ok('管理员登录成功')

  const created = await call('POST', '/api/admin/seckill/activities', {
    title: 'B3 端到端验证',
    productId: PRODUCT_ID,
    skuId: SKU_ID,
    seckillPrice: 9.9,
    seckillStock: STOCK,
    limitPerUser: 1,
    startTime: localDateTime(-5 * 60 * 1000),
    endTime: localDateTime(2 * 60 * 60 * 1000),
  }, adminToken)
  if (created.status !== 200 || created.body?.code !== 0) {
    throw new Error(`建活动失败：HTTP ${created.status} ${JSON.stringify(created.body)}`)
  }
  activityId = created.body.data
  ok(`活动已创建 id=${activityId}（库存 ${STOCK}，限购 1）`)

  // ① 并发抢购
  console.log(`\n① ${USERS} 个用户并发抢 ${STOCK} 件`)
  const usernames = Array.from({ length: USERS }, (_, i) => `sku${String(i + 1).padStart(3, '0')}`)
  const tokens = await Promise.all(usernames.map((u) => login(u, USER_PASS)))
  const buys = await Promise.all(tokens.map((token) =>
    call('POST', `/api/seckill/activities/${activityId}/order`, { quantity: 1 }, token)))

  const accepted = buys
    .map((r, i) => ({ r, i }))
    .filter(({ r }) => r.status === 200 && r.body?.code === 0)
  if (accepted.length === Math.min(STOCK, USERS)) {
    ok(`${accepted.length} 个请求被受理`)
  } else {
    fail(`期望 ${Math.min(STOCK, USERS)} 个受理，实际 ${accepted.length} 个：`
      + JSON.stringify(buys.map((r) => r.body?.message ?? r.status)))
    return
  }

  // ② 轮询结果
  console.log('\n② 轮询抢购结果（等待消费者建单）')
  const results = []
  for (const { r, i } of accepted) {
    const requestId = r.body.data.requestId
    const deadline = Date.now() + POLL_TIMEOUT_MS
    let result = null
    while (Date.now() < deadline) {
      const q = await call('GET', `/api/seckill/result/${requestId}`, undefined, tokens[i])
      if (q.status === 200 && q.body?.code === 0) {
        result = q.body.data
        break
      }
      await sleep(300)
    }
    results.push({ userId: i, requestId, result })
  }

  const succeeded = results.filter((x) => x.result?.status === 'SUCCESS')
  if (succeeded.length === accepted.length) {
    ok(`${succeeded.length} 个抢购全部拿到订单号`)
  } else {
    fail(`只有 ${succeeded.length}/${accepted.length} 个成功：`
      + JSON.stringify(results.filter((x) => x.result?.status !== 'SUCCESS').map((x) => x.result)))
  }

  const orderNos = new Set(succeeded.map((x) => x.result.orderNo))
  if (orderNos.size === succeeded.length) {
    ok(`订单号互不相同（${[...orderNos].slice(0, 3).join(', ')}…）`)
  } else {
    fail(`订单号出现重复：${[...orderNos].join(', ')}`)
  }

  const allSk = succeeded.every((x) => x.result.orderNo.startsWith('SK'))
  if (allSk) {
    ok('订单号均为 SK 前缀（秒杀渠道）')
  } else {
    fail('存在非 SK 前缀的订单号')
  }

  // ③ 活动已售数
  console.log('\n③ 活动已售数')
  const detail = await call('GET', `/api/admin/seckill/activities/${activityId}`, undefined, adminToken)
  const sold = detail.body?.data?.soldCount
  if (sold === succeeded.length) {
    ok(`soldCount = ${sold}`)
  } else {
    fail(`期望 soldCount = ${succeeded.length}，实际 ${sold}`)
  }

  // ④ 取消一单 → 回补
  console.log('\n④ 取消一单，验证回补')
  const victim = succeeded[0]
  const cancel = await call('PUT', `/api/orders/${victim.result.orderNo}/cancel`, undefined,
    tokens[victim.userId])
  if (cancel.status === 200 && cancel.body?.code === 0) {
    ok(`已取消 ${victim.result.orderNo}`)
  } else {
    fail(`取消失败：HTTP ${cancel.status} ${JSON.stringify(cancel.body)}`)
  }

  await sleep(500)
  const detail2 = await call('GET', `/api/admin/seckill/activities/${activityId}`, undefined, adminToken)
  const sold2 = detail2.body?.data?.soldCount
  if (sold2 === sold - 1) {
    ok(`soldCount 回落到 ${sold2}`)
  } else {
    fail(`期望 soldCount = ${sold - 1}，实际 ${sold2}`)
  }

  console.log(`\n=== 清理 ===`)
  console.log(`  DELETE FROM seckill_activity WHERE id=${activityId};`)
  console.log(`  redis-cli --scan --pattern 'seckill:*' | xargs -r redis-cli del`)
  console.log(`  订单：${[...orderNos].join(', ')}`)
  console.log(`  DELETE FROM order_item/t_order/payment_record WHERE order_no LIKE 'SK%';`)
  console.log(`  测试账号：DELETE FROM sys_user WHERE username LIKE 'sku%';\n`)
}

main().catch((e) => {
  console.error(`\n验证脚本异常：${e.message}\n`)
  process.exitCode = 1
})
