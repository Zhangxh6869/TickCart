import { test, expect } from '@playwright/test'

/**
 * G5 系统测试 · E1：下单主链路。
 *
 * <p>覆盖「登录 → 搜索 → 详情 → 加购 → 结算 → 支付 → 订单可见」全流程。
 * 后端业务逻辑已经由 16 个集成测试验证过，这里要抓的是**浏览器里真实跑起来才会暴露的问题**：
 * 路由跳转、登录态传递、SKU 面板交互、支付后的页面流转。</p>
 *
 * <p><b>注意</b>：这条用例会往开发库写真实数据（扣库存、生成订单）。</p>
 */
test.describe('下单主链路', () => {
  test('登录 → 搜索 → 加购 → 结算 → 支付 → 订单可见', async ({ page }) => {
    // ---- 1. 登录 ----
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('demo')
    await page.getByPlaceholder('请输入密码').fill('123456')
    await page.locator('button[type="submit"]').click()

    // 登录成功会离开登录页
    await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 })

    // ---- 2. 搜索 ----
    await page.goto('/search')
    const searchInput = page.getByPlaceholder('搜索商品')
    await searchInput.fill('牛油果')
    await searchInput.press('Enter')

    const firstProduct = page.locator('.product-card').first()
    await expect(firstProduct).toBeVisible({ timeout: 20_000 })
    await firstProduct.click()

    // ---- 3. 详情页加购 ----
    await expect(page).toHaveURL(/\/products\//, { timeout: 20_000 })

    // 底部「加入购物车」先弹 SKU 面板，面板里再确认一次
    await page.getByText('加入购物车').first().click()
    await page.getByText('加入购物车').last().click()

    // ---- 4. 购物车 → 结算 ----
    await page.goto('/cart')
    const checkoutBtn = page.getByRole('button', { name: /结算/ })
    await expect(checkoutBtn).toBeVisible({ timeout: 20_000 })
    await checkoutBtn.click()

    // 地址是异步加载的：selectedAddress 为空时 onSubmit 会直接 return（只弹 toast、不发请求），
    // 所以必须等地址真正渲染出来再点提交。
    await expect(page.locator('.card__name')).toBeVisible({ timeout: 20_000 })

    // 确认订单页与 address / order-detail 保持一致：底部 tabbar 让位。
    // 之前它没进 FULLSCREEN_ROUTES，靠 submit-bar 层级更高才没露馅（两者逐像素重叠）。
    await expect(page.locator('.van-tabbar')).toBeHidden()

    // ---- 5. 确认订单 → 提交 ----
    const submitOrder = page.getByRole('button', { name: '提交订单' })
    await expect(submitOrder).toBeVisible({ timeout: 20_000 })
    await submitOrder.click()

    // 提交后落在「订单详情 · 待付款」，需要再点「去支付」才进收银台
    const goPay = page.getByRole('button', { name: '去支付' })
    await expect(goPay).toBeVisible({ timeout: 20_000 })
    await goPay.click()

    // ---- 6. 支付 ----
    const confirmPay = page.getByRole('button', { name: /确认支付/ })
    await expect(confirmPay).toBeVisible({ timeout: 20_000 })
    await confirmPay.click()

    // 模拟渠道页
    const confirmChannelPay = page.getByRole('button', { name: '确认付款' })
    await expect(confirmChannelPay).toBeVisible({ timeout: 20_000 })
    await confirmChannelPay.click()

    // ---- 7. 订单列表能看到 ----
    await page.goto('/orders')
    await expect(page.locator('.order').first()).toBeVisible({ timeout: 20_000 })
  })
})
