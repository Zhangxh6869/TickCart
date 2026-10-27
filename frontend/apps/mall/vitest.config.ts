import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

/**
 * 单元测试配置（mall）。
 *
 * <p><b>为什么 merge 而不是重写一份</b>：`@` 别名在 `vite.config.ts` 里只写了一遍，
 * 这里复用同一份，避免「构建能跑、测试报模块找不到」这类只在一边出现的分歧。
 * 注意 `tsconfig.json` 的 `paths` 只影响类型检查，vitest 运行时读的是 vite 的 `resolve.alias`。</p>
 *
 * <p><b>为什么 environment 是 node 而不是 jsdom</b>：本目录的测试全是纯逻辑
 * （金额格式化、结算路由解析、倒计时、store、分享链接），不渲染组件、不碰真实 DOM。
 * 需要 `window`/`navigator` 的地方用 `vi.stubGlobal` 打桩即可，不必为此装一整套 jsdom。
 * 将来要测组件时再单独加 environment 与 `@vue/test-utils`。</p>
 *
 * <p><b>测试文件为什么放 `tests/` 而不是 `src/`</b>：`build` 第一步是 `vue-tsc --noEmit`，
 * 而 tsconfig 的 `include` 是 `src/**`。放 `src/` 里会让测试参与构建期类型检查，
 * 一改测试就可能让构建失败；放 `tests/` 则完全隔离。</p>
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'node',
      include: ['tests/**/*.spec.ts'],
      // 不开 globals：显式 import { describe, it, expect }，避免往 tsconfig 塞 vitest 类型
      globals: false,
    },
  }),
)
