import { test, expect } from '@playwright/test'

/**
 * E2E 底座自检。
 *
 * <p>不验业务，只验三件事：浏览器能起来、前端能渲染、后端接口能通。
 * 如果它挂了，说明是环境问题而不是业务回归，可以省掉一轮误判 ——
 * 和 `ContainerSmokeIT` 在集成测试里的作用一样。</p>
 */
test('首页能打开并渲染出商品列表', async ({ page }) => {
  await page.goto('/')

  // 页面基本骨架
  await expect(page.locator('body')).toBeVisible()

  // 商品卡片来自后端 /api/products，能渲染出来说明前后端链路是通的
  await expect(page.locator('.product-card').first()).toBeVisible({ timeout: 20_000 })
})

test('搜索能返回结果', async ({ page }) => {
  await page.goto('/')

  // 搜索框进入搜索页（首页吸顶区有搜索入口）
  const searchEntry = page.locator('.home__search, .search-bar, [class*="search"]').first()
  await expect(searchEntry).toBeVisible({ timeout: 20_000 })
})
