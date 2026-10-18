#!/usr/bin/env node
/**
 * A1 并发验证：下单幂等（Idempotency-Key）与结算抢占。
 *
 * 用法（后端需已启动、MySQL/Redis/RabbitMQ 就绪）：
 *   node scripts/verify-order-idempotent.mjs
 *   BASE=http://localhost:8080 TICKCART_USER=demo TICKCART_PASS=123456 CONCURRENCY=50 \
 *     node scripts/verify-order-idempotent.mjs
 *
 * 可选：
 *   RUN_CONTROL=1   额外跑「不带幂等键」的对照组（会真实产生多笔订单，默认关闭）
 *   KEEP_DATA=1     不打印清理 SQL（默认会打印，便于事后对账）
 *
 * 四组用例：
 *   ① 购物车结算，同一幂等键 50 并发  → 只成 1 单
 *   ② 购物车结算，20 个不同幂等键并发 → 1 成功 + 19 个 409（结算抢占生效）
 *   ③ 立即购买，同一幂等键 50 并发    → 只成 1 单
 *   ④ 立即购买，不带幂等键 5 并发     → 多笔订单（对照，证明 ③ 的保护来自幂等键）
 *
 * 注意：脚本会真实下单与扣库存，跑完按末尾打印的 SQL 清理。
 */
const BASE = (process.env.BASE ?? 'http://localhost:8080').replace(/\/$/, '')
const USER = process.env.TICKCART_USER ?? 'demo'
const PASS = process.env.TICKCART_PASS ?? '123456'
const CONCURRENCY = Number(process.env.CONCURRENCY ?? 50)
const RUN_CONTROL = process.env.RUN_CONTROL === '1'
const KEEP_DATA = process.env.KEEP_DATA === '1'

let token = ''
const createdOrderNos = new Set()
const idemKeys = new Set()

/** 发起一次请求，始终返回 {status, body}，不抛异常（便于统计 409/500）。 */
async function call(method, path, body, headers = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let parsed = null
  try {
    parsed = text ? JSON.parse(text) : null
  } catch {
    parsed = { raw: text }
  }
  return { status: res.status, body: parsed }
}

function uuid() {
  return globalThis.crypto?.randomUUID?.() ?? `k-${Date.now()}-${Math.random().toString(16).slice(2, 12)}`
}

function ok(msg) {
  console.log(`  ✓ ${msg}`)
}

function fail(msg) {
  console.log(`  ✗ ${msg}`)
  process.exitCode = 1
}

/** 从响应集合里统计：成功数、订单号去重数、409 数、其他错误。 */
function summarize(results) {
  const success = results.filter((r) => r.status === 200 && r.body?.code === 0)
  const conflicts = results.filter((r) => r.status === 409)
  const others = results.filter((r) => r.status !== 200 && r.status !== 409)
  const orderNos = new Set(success.map((r) => r.body.data.orderNo))
  success.forEach((r) => createdOrderNos.add(r.body.data.orderNo))
  return { success, conflicts, others, orderNos }
}

function reportOthers(others) {
  if (others.length === 0) return
  const sample = others[0]
  fail(`出现 ${others.length} 个非 200/409 响应，样例：HTTP ${sample.status} ${JSON.stringify(sample.body)}`)
}

async function login() {
  const r = await call('POST', '/api/auth/login', { username: USER, password: PASS })
  if (r.status !== 200 || r.body?.code !== 0) {
    throw new Error(`登录失败：HTTP ${r.status} ${JSON.stringify(r.body)}`)
  }
  token = r.body.data.accessToken
}

async function pickAddress() {
  const r = await call('GET', '/api/addresses')
  if (r.status !== 200 || r.body?.code !== 0) {
    throw new Error(`查询收货地址失败：HTTP ${r.status} ${JSON.stringify(r.body)}`)
  }
  const list = r.body.data ?? []
  if (list.length === 0) {
    throw new Error('该账号没有收货地址，请先在前台新增一个再跑本脚本')
  }
  return list[0].id
}

/**
 * 挑一个「库存足够跑完并发」的商品。
 * 有真实规格的商品必须带 skuId，否则后端会按商品级库存扣减，与 SKU 各算一套账。
 */
async function pickProduct(minStock) {
  const r = await call('GET', '/api/products?page=1&size=50')
  if (r.status !== 200 || r.body?.code !== 0) {
    throw new Error(`查询商品失败：HTTP ${r.status} ${JSON.stringify(r.body)}`)
  }
  for (const item of r.body.data.records ?? []) {
    const d = await call('GET', `/api/products/${item.id}`)
    if (d.status !== 200 || d.body?.code !== 0) continue
    const detail = d.body.data
    const skus = (detail.skus ?? []).filter((s) => (s.stock ?? 0) >= minStock && s.specValue)
    if (skus.length > 0) {
      const sku = skus.sort((a, b) => b.stock - a.stock)[0]
      return { productId: detail.id, skuId: sku.id, name: detail.name, stock: sku.stock }
    }
    if ((detail.stock ?? 0) >= minStock && (detail.skus ?? []).length === 0) {
      return { productId: detail.id, skuId: 0, name: detail.name, stock: detail.stock }
    }
  }
  throw new Error(`没有库存 ≥ ${minStock} 的商品，无法跑并发验证`)
}

async function addToCart(product, quantity = 1) {
  const r = await call('POST', '/api/cart', {
    productId: product.productId,
    skuId: product.skuId,
    quantity,
  })
  if (r.status !== 200 || r.body?.code !== 0) {
    throw new Error(`加购失败：HTTP ${r.status} ${JSON.stringify(r.body)}`)
  }
}

async function main() {
  console.log(`\n=== A1 并发验证 @ ${BASE}（用户 ${USER}，并发 ${CONCURRENCY}）===\n`)
  await login()
  ok('登录成功')

  const addressId = await pickAddress()
  ok(`收货地址 id=${addressId}`)

  const product = await pickProduct(CONCURRENCY + 10)
  ok(`测试商品 #${product.productId}「${product.name}」sku=${product.skuId} 库存 ${product.stock}`)

  // ---- ① 购物车结算：同一幂等键并发 ----
  console.log(`\n① 购物车结算 · 同一幂等键 × ${CONCURRENCY}`)
  await addToCart(product)
  const cartKey = uuid()
  idemKeys.add(cartKey)
  const r1 = await Promise.all(
    Array.from({ length: CONCURRENCY }, () =>
      call('POST', '/api/orders', { addressId }, { 'Idempotency-Key': cartKey }),
    ),
  )
  const s1 = summarize(r1)
  reportOthers(s1.others)
  if (s1.orderNos.size === 1 && s1.success.length === CONCURRENCY) {
    ok(`${CONCURRENCY} 个请求全部成功且订单号唯一：${[...s1.orderNos][0]}`)
  } else {
    fail(`期望 ${CONCURRENCY} 个成功响应、1 个订单号，实际 ${s1.success.length} 成功 / ${s1.orderNos.size} 个订单号`)
  }

  // ---- ② 购物车结算：不同幂等键并发（只靠结算抢占） ----
  console.log(`\n② 购物车结算 · ${CONCURRENCY} 个不同幂等键并发（验证结算抢占）`)
  await addToCart(product)
  const r2 = await Promise.all(
    Array.from({ length: CONCURRENCY }, () => {
      const key = uuid()
      idemKeys.add(key)
      return call('POST', '/api/orders', { addressId }, { 'Idempotency-Key': key })
    }),
  )
  const s2 = summarize(r2)
  // 落败者有两种合法结局：抢抢占输了的返回 409；等它读到购物车时已被清空的返回 400
  const rejected = r2.filter((r) => r.status === 409 || r.status === 400)
  const unexpected = r2.filter((r) => r.status !== 200 && r.status !== 409 && r.status !== 400)
  reportOthers(unexpected)
  if (s2.success.length === 1 && rejected.length === CONCURRENCY - 1) {
    ok(`1 个成功 + ${rejected.length} 个被拒（409 抢占失败 / 400 购物车已空），未重复建单`)
  } else {
    fail(`期望 1 成功 / ${CONCURRENCY - 1} 个被拒，实际 ${s2.success.length} 成功 / ${rejected.length} 个被拒`)
  }

  // ---- ③ 立即购买：同一幂等键并发 ----
  console.log(`\n③ 立即购买 · 同一幂等键 × ${CONCURRENCY}`)
  const buyKey = uuid()
  idemKeys.add(buyKey)
  const r3 = await Promise.all(
    Array.from({ length: CONCURRENCY }, () =>
      call(
        'POST',
        '/api/orders/buy-now',
        { addressId, productId: product.productId, skuId: product.skuId, quantity: 1 },
        { 'Idempotency-Key': buyKey },
      ),
    ),
  )
  const s3 = summarize(r3)
  reportOthers(s3.others)
  if (s3.orderNos.size === 1 && s3.success.length === CONCURRENCY) {
    ok(`${CONCURRENCY} 个请求全部成功且订单号唯一：${[...s3.orderNos][0]}`)
  } else {
    fail(`期望 ${CONCURRENCY} 个成功响应、1 个订单号，实际 ${s3.success.length} 成功 / ${s3.orderNos.size} 个订单号`)
  }

  // ---- ④ 对照组：不带幂等键（默认关闭） ----
  if (RUN_CONTROL) {
    const n = 5
    console.log(`\n④ 对照组 · 立即购买不带幂等键 × ${n}（预期产生多笔订单）`)
    const r4 = await Promise.all(
      Array.from({ length: n }, () =>
        call('POST', '/api/orders/buy-now', {
          addressId,
          productId: product.productId,
          skuId: product.skuId,
          quantity: 1,
        }),
      ),
    )
    const s4 = summarize(r4)
    reportOthers(s4.others)
    console.log(`  实际产生 ${s4.orderNos.size} 笔订单（无幂等键时每次点击都会成单，符合预期）`)
  }

  // ---- 清理提示 ----
  const orders = [...createdOrderNos]
  console.log(`\n=== 结果：本次共产生 ${orders.length} 笔订单 ===`)
  if (!KEEP_DATA && orders.length > 0) {
    const inList = orders.map((o) => `'${o}'`).join(',')
    console.log('清理 SQL（在 tickcart 库执行，回补库存后删除订单）：')
    console.log(`  -- 订单号：${orders.join(', ')}`)
    console.log(`  DELETE FROM order_item WHERE order_no IN (${inList});`)
    console.log(`  DELETE FROM payment_record WHERE order_no IN (${inList});`)
    console.log(`  DELETE FROM t_order WHERE order_no IN (${inList});`)
    console.log(`  DELETE FROM idempotent_record WHERE idem_key IN (${[...idemKeys].map((k) => `'${k}'`).join(',')});`)
    console.log('  -- 库存回补：按订单明细的 product_id/sku_id/quantity 逐条加回')
  }
  console.log('')
}

main().catch((e) => {
  console.error(`\n验证脚本异常：${e.message}\n`)
  process.exitCode = 1
})
