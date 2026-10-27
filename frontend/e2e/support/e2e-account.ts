import type { APIRequestContext } from '@playwright/test'

/**
 * E2E 专用账号与数据准备。
 *
 * <p><b>为什么不用 demo 账号</b>：demo 是给人手动体验的。E2E 每跑一次就往它名下塞一张
 * 已支付订单、把购物车填满，人再打开页面看到的就是一堆测试残留，分不清哪些是自己点的。
 * 换成一个专用账号后，污染范围就收敛在 `e2e_runner` 名下。</p>
 *
 * <p><b>账号怎么来</b>：注册接口是幂等的入口 —— 先尝试注册，失败（用户名已存在）就当作已就绪，
 * 然后统一走登录拿 token。这样本地反复跑、CI 全新库跑，都是同一条路径，不需要分支。</p>
 */
export const E2E_USER = 'e2e_runner'
/** 密码规则：8~32 位且必须同时含字母与数字 */
export const E2E_PASSWORD = 'e2ePass123'

/** 收货地址快照。CI 的种子数据里没有地址，必须自己建。 */
const ADDRESS = {
  receiver: 'E2E 机器人',
  phone: '13800000000',
  province: '广东省',
  city: '深圳市',
  district: '南山区',
  detail: '科技园路 1 号',
  isDefault: 1,
}

/** 下单主链路会买的商品（搜索「牛油果」的第一个结果）。 */
const TARGET_PRODUCT_ID = 302

function authHeader(token: string) {
  return { Authorization: `Bearer ${token}` }
}

/**
 * 注册（若已存在则跳过）→ 登录 → 确保有收货地址，返回 access token。
 *
 * <p>任一步失败都直接抛：E2E 的前置条件不满足时，宁可让整个 suite 立刻红掉并给出原因，
 * 也不要在 spec 里等到 20 秒超时才发现「原来是没登录上」。</p>
 */
export async function ensureAccount(ctx: APIRequestContext): Promise<string> {
  // 注册失败是预期内的（账号已存在），不检查返回值
  await ctx.post('/api/auth/register', { data: { username: E2E_USER, password: E2E_PASSWORD } })

  const login = await ctx.post('/api/auth/login', {
    data: { username: E2E_USER, password: E2E_PASSWORD },
  })
  if (!login.ok()) {
    throw new Error(`E2E 账号登录失败：${login.status()} ${await login.text()}`)
  }
  const token: string = (await login.json()).data.accessToken

  await ensureAddress(ctx, token)
  return token
}

/** 没有收货地址就建一条（种子数据里没有，CI 全新库必然触发）。 */
async function ensureAddress(ctx: APIRequestContext, token: string): Promise<void> {
  const listResp = await ctx.get('/api/addresses', { headers: authHeader(token) })
  const addresses = (await listResp.json()).data ?? []
  if (addresses.length > 0) {
    return
  }
  const created = await ctx.post('/api/addresses', {
    headers: authHeader(token),
    data: ADDRESS,
  })
  if (!created.ok()) {
    throw new Error(`创建收货地址失败：${created.status()} ${await created.text()}`)
  }
}

/**
 * 清空购物车。
 *
 * <p>这是「跑前重置」的核心：上一次跑如果中途失败，购物车里会留着商品，
 * 下次结算的金额和件数就变了 —— 这类残留不会让断言立刻失败，只会让失败原因变得难以解释。
 * 逐项删除而不是「全选 + 删除已勾选」：失效商品（下架/无库存）不参与勾选，那样清不干净。</p>
 */
export async function clearCart(ctx: APIRequestContext, token: string): Promise<number> {
  const resp = await ctx.get('/api/cart', { headers: authHeader(token) })
  if (!resp.ok()) {
    throw new Error(`读取购物车失败：${resp.status()} ${await resp.text()}`)
  }
  const items: Array<{ id: number }> = (await resp.json()).data?.items ?? []
  for (const item of items) {
    await ctx.delete(`/api/cart/${item.id}`, { headers: authHeader(token) })
  }
  return items.length
}

/**
 * 库存守卫。
 *
 * <p>E2E 每跑一次就真买一件，库存只减不增（订单已支付，不能取消回补）。
 * 本地库跑够多次就会把这个商品买空，届时失败原因会是「结算按钮不可点」，
 * 与真正的回归混在一起。这里提前把库存打出来，低于阈值时告警 ——
 * <b>只告警不失败</b>：库存低不代表代码有问题，不该因此拦住流水线。</p>
 */
export async function checkStock(ctx: APIRequestContext): Promise<number | null> {
  const resp = await ctx.get(`/api/products/${TARGET_PRODUCT_ID}`)
  if (!resp.ok()) {
    return null
  }
  const stock: number | undefined = (await resp.json()).data?.stock
  if (typeof stock === 'number' && stock < 5) {
    console.warn(
      `\n⚠️  E2E 目标商品（id=${TARGET_PRODUCT_ID}）库存只剩 ${stock} 件。\n` +
        `   E2E 每跑一次会真实卖出 1 件且不会回补，库存耗尽后下单链路会失败。\n` +
        `   处理：重置开发库（重新跑 Flyway 种子）或手动补货。\n`,
    )
  }
  return stock ?? null
}
