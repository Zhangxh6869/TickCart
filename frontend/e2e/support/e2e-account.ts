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
/** 登录账号已统一为手机号：E2E 账号用这个号注册与登录（137 段避开种子与压测账号） */
export const E2E_PHONE = '13700000001'
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
 * 登录（账号已就绪则直接返回）→ 确保有收货地址，返回 access token。
 *
 * <p>改造后登录账号是手机号、注册必须带短信验证码，所以不能再「先注册、失败就当作已存在」：
 * 先直接登录，成功就跳过注册，既不浪费短信额度，也不会反复撞 30 秒发送间隔。</p>
 *
 * <p>任一步失败都直接抛：E2E 的前置条件不满足时，宁可让整个 suite 立刻红掉并给出原因，
 * 也不要在 spec 里等到 20 秒超时才发现「原来是没登录上」。</p>
 */
export async function ensureAccount(ctx: APIRequestContext): Promise<string> {
  const existing = await ctx.post('/api/auth/login', {
    data: { phone: E2E_PHONE, password: E2E_PASSWORD },
  })
  if (existing.ok()) {
    const token: string = (await existing.json()).data.accessToken
    await ensureAddress(ctx, token)
    return token
  }

  await registerAccount(ctx)

  const login = await ctx.post('/api/auth/login', {
    data: { phone: E2E_PHONE, password: E2E_PASSWORD },
  })
  if (!login.ok()) {
    throw new Error(`E2E 账号登录失败：${login.status()} ${await login.text()}`)
  }
  const token: string = (await login.json()).data.accessToken

  await ensureAddress(ctx, token)
  return token
}

/**
 * 注册 E2E 专用账号：发 scene=1 验证码 → 从 devCode 取码 → 带 code 注册。
 *
 * <p>devCode 只在后端 Mock 模式下回显，因此 E2E 必须跑在 `tickcart.sms.mock-enabled=true`
 * 的环境里（本地与 CI 都是）。</p>
 */
async function registerAccount(ctx: APIRequestContext): Promise<void> {
  const sent = await ctx.post('/api/auth/sms/send', { data: { phone: E2E_PHONE, scene: 1 } })
  if (!sent.ok()) {
    throw new Error(`E2E 账号注册失败：验证码发送被拒 ${sent.status()} ${await sent.text()}`)
  }
  const code = (await sent.json()).data?.devCode
  if (!code) {
    throw new Error('E2E 账号注册失败：响应里没有 devCode，后端需开启 tickcart.sms.mock-enabled')
  }

  const created = await ctx.post('/api/auth/register', {
    data: { username: E2E_USER, phone: E2E_PHONE, password: E2E_PASSWORD, code },
  })
  if (created.ok()) {
    return
  }

  const body = await created.text()
  // 改造前注册的手机号是可选的：旧库里的 e2e_runner 没有手机号，会占着用户名让这里 409
  if (created.status() === 409 && body.includes('用户名已存在')) {
    throw new Error(
      `E2E 账号注册失败：用户名 ${E2E_USER} 已被占用，但该账号没有手机号 ${E2E_PHONE}。\n` +
        "这是改造前遗留的账号，请先删除：DELETE FROM tickcart.sys_user WHERE username = 'e2e_runner';",
    )
  }
  throw new Error(`E2E 账号注册失败：${created.status()} ${body}`)
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
 * <p>E2E 每跑一次就真买一件。正常情况下收尾阶段会通过退款把这件货退掉、库存回补
 * （见 `support/e2e-refund.ts`），所以长期跑库存应当稳定；<b>这里的告警是那条链路失效的信号</b> ——
 * 退款一旦出问题，库存就会重新开始净减，跑够次数会把规格买空，届时失败原因会是
 * 「结算按钮不可点」，与真正的回归混在一起。</p>
 *
 * <p><b>为什么看 SKU 而不是商品汇总</b>：加购扣的是具体规格的库存
 * （`product_sku.stock`），商品上的 `stock` 只是派生汇总。实测两者差得很远
 * （商品 187 / SKU 107 / SKU 80），按汇总判断会把可跑次数高估近一倍。
 * 取所有规格的最小值 —— 测试点哪个规格不确定，按最紧的算才安全。</p>
 *
 * <p><b>只告警不失败</b>：库存低不代表代码有问题，不该因此拦住流水线。</p>
 */
export async function checkStock(ctx: APIRequestContext): Promise<number | null> {
  const resp = await ctx.get(`/api/products/${TARGET_PRODUCT_ID}`)
  if (!resp.ok()) {
    return null
  }
  const detail = (await resp.json()).data
  const skus: Array<{ id: number; stock: number }> = detail?.skus ?? []
  const effective: number | undefined =
    skus.length > 0 ? Math.min(...skus.map((sku) => sku.stock)) : detail?.stock
  const breakdown =
    skus.length > 0
      ? `最紧规格 ${effective} 件（${skus.map((s) => `#${s.id}=${s.stock}`).join(' / ')}；商品汇总 ${detail?.stock}）`
      : `商品汇总 ${effective} 件`
  console.log(`[e2e] 目标商品库存：${breakdown}`)

  if (typeof effective === 'number' && effective < 5) {
    console.warn(
      `\n⚠️  E2E 目标商品（id=${TARGET_PRODUCT_ID}）库存告急：${breakdown}\n` +
        `   收尾阶段本应通过退款把每轮卖出的 1 件补回来，库存持续下降说明退款链路没生效\n` +
        `   （看上一轮收尾的「退款回补失败」告警）。\n` +
        `   处理：修好收尾逻辑后重跑一次（会顺带补回历史欠账），或手动补货。\n`,
    )
  }
  return effective ?? null
}
