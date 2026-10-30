import type { APIRequestContext } from '@playwright/test'

/**
 * E2E 收尾：用**真实的退款链路**把测试消耗掉的库存还回去。
 *
 * <p><b>为什么不是直接改库存</b>：`PUT /api/admin/skus/{skuId}/stock?stock=N` 是绝对值写入，
 * 要先把跑之前的库存记下来才能写回（跨进程传状态），读写之间有并发下单就会被覆盖；
 * 而且它只动库存，不动销量、不动订单状态。退款是**相对**回补：卖了几件就还几件，
 * 不需要基线；销量由后端一起回退；订单落到终态「已退款」，不再堆在后台「待发货」待办里。</p>
 *
 * <p><b>为什么不是让订单「可取消」</b>：已支付订单不可取消是业务规则，为了测试方便放宽它
 * 是把成本从测试转嫁到业务上。退款才是用户真实会走的路。</p>
 *
 * <p><b>失败了怎么办</b>：只告警不抛 —— 收尾抛异常会把原本通过的用例标成失败。
 * 代价是退款链路若真的坏了，库存会重新开始净减；`global-setup` 的低库存守卫会提示，
 * 这里也会逐笔打印失败原因。</p>
 */
export interface RefundCleanupStats {
  /** 成功退款并回补库存的订单数 */
  refunded: number
  /** 退款失败的订单数（只告警，不影响用例结果） */
  failed: number
}

/** 后台管理员账号：Flyway 种子固定 admin/admin123；允许环境变量覆盖，换库时不必改代码 */
const ADMIN_USER = process.env.E2E_ADMIN_USER ?? 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin123'

/**
 * 订单状态「待发货」（1）：E2E 下单主链路支付成功后停在这里，
 * 也是「未发货退款」唯一可申请的状态（`RefundService.refundTypeOf`）。
 */
const STATUS_PENDING_SHIPMENT = 1

/** 后端分页 size 上限是 50（`OrderQuery.getSize` 会截断），超过就翻页 */
const PAGE_SIZE = 50
/** 翻页上限：单次收尾最多处理 500 笔，够用且防止死循环 */
const MAX_PAGES = 10

/** 退款原因码 1 = 「不想要了」，是发货前反悔的最高频原因，也最贴合「测试收尾」的语义 */
const REASON_NO_LONGER_WANTED = 1
const REASON_TEXT = 'E2E 收尾：回补测试消耗的库存'

function authHeader(token: string) {
  return { Authorization: `Bearer ${token}` }
}

/** 后台登录拿管理员令牌（审核退款需要 ROLE_ADMIN）。 */
export async function ensureAdminToken(ctx: APIRequestContext): Promise<string> {
  const login = await ctx.post('/api/auth/login', {
    data: { username: ADMIN_USER, password: ADMIN_PASSWORD },
  })
  if (!login.ok()) {
    throw new Error(`管理员登录失败：${login.status()} ${await login.text()}`)
  }
  return (await login.json()).data.accessToken
}

/**
 * 列出该账号名下所有「待发货」订单号。
 *
 * <p><b>必须先取完再逐笔退款</b>：退款会把订单从 `status=1` 的筛选结果里移走，
 * 边翻页边退款会导致翻页游标漂移、漏掉一部分订单。</p>
 */
async function listPendingShipmentOrders(ctx: APIRequestContext, token: string): Promise<string[]> {
  const orderNos: string[] = []
  for (let page = 1; page <= MAX_PAGES; page++) {
    const resp = await ctx.get(
      `/api/orders?status=${STATUS_PENDING_SHIPMENT}&page=${page}&size=${PAGE_SIZE}`,
      { headers: authHeader(token) },
    )
    if (!resp.ok()) {
      throw new Error(`读取待发货订单失败：${resp.status()} ${await resp.text()}`)
    }
    const data = (await resp.json()).data
    const records: Array<{ orderNo: string }> = data?.records ?? []
    orderNos.push(...records.map((record) => record.orderNo))
    if (records.length < PAGE_SIZE || orderNos.length >= (data?.total ?? 0)) {
      break
    }
  }
  return orderNos
}

/**
 * 单笔订单走完「用户申请退款 → 管理员同意」。
 *
 * <p>两步都必须走接口，不能在测试里直接改状态：库存回补发生在 `approve` 内部，
 * 且只在条件 UPDATE 抢到状态流转时才执行一次（幂等保护）。</p>
 */
async function refundAndApprove(
  ctx: APIRequestContext,
  userToken: string,
  adminToken: string,
  orderNo: string,
): Promise<void> {
  const applied = await ctx.post(`/api/orders/${orderNo}/refund`, {
    headers: authHeader(userToken),
    data: { reasonCode: REASON_NO_LONGER_WANTED, reason: REASON_TEXT },
  })
  if (!applied.ok()) {
    throw new Error(`申请退款失败：${applied.status()} ${await applied.text()}`)
  }
  const refundNo: string = (await applied.json()).data.refundNo

  const approved = await ctx.post(`/api/admin/refunds/${refundNo}/approve`, {
    headers: authHeader(adminToken),
  })
  if (!approved.ok()) {
    throw new Error(`审核退款失败：${approved.status()} ${await approved.text()}`)
  }
}

/**
 * 把专用账号名下的「待发货」订单全部退掉，回补库存与销量。
 *
 * <p>顺带清掉历史欠账：之前跑 E2E 留下的待发货订单也会一并退掉，
 * 所以第一次接上这个收尾时，库存会一次性补回好几件。</p>
 */
export async function settlePaidOrders(
  ctx: APIRequestContext,
  userToken: string,
): Promise<RefundCleanupStats> {
  const orderNos = await listPendingShipmentOrders(ctx, userToken)
  if (orderNos.length === 0) {
    return { refunded: 0, failed: 0 }
  }

  // 管理员令牌按需获取：没有待处理订单时不必多打一次登录
  const adminToken = await ensureAdminToken(ctx)
  let refunded = 0
  let failed = 0
  for (const orderNo of orderNos) {
    try {
      await refundAndApprove(ctx, userToken, adminToken, orderNo)
      refunded++
    } catch (error) {
      // 单笔失败不打断其余订单：可能只是某笔订单已被处理过
      failed++
      console.warn(`[e2e] 退款回补失败：${orderNo}`, error)
    }
  }
  return { refunded, failed }
}
