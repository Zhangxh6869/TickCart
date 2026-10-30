import { request, type FullConfig } from '@playwright/test'
import { clearCart, ensureAccount } from './support/e2e-account'
import { settlePaidOrders } from './support/e2e-refund'

/**
 * E2E 全局收尾：清空购物车 + 把本轮的已支付订单退款，回补库存。
 *
 * <p><b>为什么必须回补库存</b>：下单主链路会真实下单并支付，库存只减不增。
 * 已支付订单在状态机上不允许取消（这是正确的业务规则），所以本地库跑够次数就会
 * 把目标规格买空 —— 届时的失败原因是「结算按钮不可点」，与真正的代码回归混在一起。</p>
 *
 * <p><b>为什么走退款而不是直接写库存</b>：退款是相对回补（卖几件还几件，不需要记基线），
 * 销量由后端一并回退，订单还会落到终态「已退款」，不再堆在后台「待发货」待办里。
 * 详见 `support/e2e-refund.ts`。</p>
 *
 * <p><b>失败不外抛</b>：收尾阶段抛异常会把原本通过的用例标成失败，
 * 让人误以为是被测代码坏了。这里只告警 —— 退款失败时库存会重新开始净减，
 * 由 `global-setup` 的低库存守卫兜底提示。</p>
 */
export default async function globalTeardown(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use?.baseURL ?? 'http://localhost:5173'
  const ctx = await request.newContext({ baseURL })
  try {
    const token = await ensureAccount(ctx)
    const removed = await clearCart(ctx, token)
    if (removed > 0) {
      console.log(`\n[e2e] 收尾：清空购物车 ${removed} 项`)
    }

    const stats = await settlePaidOrders(ctx, token)
    if (stats.refunded > 0 || stats.failed > 0) {
      console.log(
        `[e2e] 收尾：退款回补库存 ${stats.refunded} 笔` +
          (stats.failed > 0 ? `，失败 ${stats.failed} 笔（库存未回补，见上方告警）` : ''),
      )
    }
  } catch (error) {
    console.warn('\n[e2e] 收尾清理失败（不影响用例结果）：', error)
  } finally {
    await ctx.dispose()
  }
}
