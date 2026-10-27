import { defineConfig, devices } from '@playwright/test'

/**
 * E2E 配置（用户端 mall）。
 *
 * <p>只跑关键旅程，不追求 UI 全覆盖 —— 目的是抓「前后端契约断裂」和「路由/状态机问题」，
 * 而不是替代手工点测。后端业务逻辑的正确性已经由 16 个集成测试覆盖，
 * 这里关注的是浏览器里真实跑起来会发生什么。</p>
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },

  // 全局前后置：准备专用账号与干净数据、跑完清购物车。
  // 放在这里而不是各 spec 的 beforeAll —— 它们是整个 suite 的前置条件，不是某条用例的步骤。
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',

  // 串行执行：E2E 会写真实数据（下单），并行会互相干扰
  fullyParallel: false,
  workers: 1,

  // CI 上重试一次，本地不重试 —— 本地重试会掩盖真实的 flaky
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],

  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    // mall 是移动端 H5，用手机视口跑。
    // channel: 'chrome' 直接用系统已安装的 Chrome，跳过 playwright install 的 130MB 下载
    // （国内访问 cdn.playwright.dev 很慢）。CI 的 ubuntu-latest 也预装 Chrome，两边一致。
    { name: 'mobile-chrome', use: { ...devices['Pixel 5'], channel: 'chrome' } },
  ],

  // 本地复用已跑着的 dev server（避免端口冲突），CI 里新起一个
  webServer: {
    command: 'pnpm --filter mall dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
