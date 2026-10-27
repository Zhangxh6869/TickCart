import { request, type FullConfig } from '@playwright/test'
import { clearCart, ensureAccount } from './support/e2e-account'

/**
 * E2E 全局收尾：把购物车清空。
 *
 * <p><b>不清理订单</b>：已支付的订单是正常业务数据，后端也没有删除接口。
 * 它们落在专用账号 `e2e_runner` 名下，不影响 demo 用户手动体验 ——
 * 这正是引入专用账号的意义：把不可避免的残留圈在一个人身上。</p>
 *
 * <p><b>失败不外抛</b>：收尾阶段抛异常会把原本通过的用例标成失败，
 * 让人误以为是被测代码坏了。这里只告警。</p>
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
  } catch (error) {
    console.warn('\n[e2e] 收尾清理失败（不影响用例结果）：', error)
  } finally {
    await ctx.dispose()
  }
}
