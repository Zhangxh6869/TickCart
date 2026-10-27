import { request, type FullConfig } from '@playwright/test'
import { checkStock, clearCart, ensureAccount } from './support/e2e-account'

/**
 * E2E 全局前置：把「测试账号 + 干净数据」准备好。
 *
 * <p>放在 globalSetup 而不是每个 spec 的 beforeAll：这些是**整个 suite 的前置条件**
 * （账号存在、有地址、购物车是空的），不是某条用例的步骤。写在 spec 里会导致
 * 每条用例各跑一遍，且失败时报错位置和真实原因错位。</p>
 *
 * <p>时序：Playwright 先起 `webServer`（5173 的 dev server），再跑 globalSetup ——
 * 所以这里可以直接用 `/api`，由 dev server 反代到后端，本地与 CI 用同一套地址。</p>
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use?.baseURL ?? 'http://localhost:5173'
  const ctx = await request.newContext({ baseURL })
  try {
    const token = await ensureAccount(ctx)
    const removed = await clearCart(ctx, token)
    const stock = await checkStock(ctx)
    console.log(
      `\n[e2e] 账号就绪：已清空购物车 ${removed} 项；目标商品库存 ${stock ?? '未知'} 件`,
    )
  } finally {
    await ctx.dispose()
  }
}
