<script setup lang="ts">
/**
 * 二级页顶部导航条：`van-nav-bar` + 自绘返回按钮（与搜索页 `.search__back` 同一套外观）。
 *
 * <p>为什么不用 `left-arrow`：Vant 自带的左箭头是**主题蓝 + 16px**
 * （`.van-nav-bar .van-icon { color: var(--van-nav-bar-icon-color) }`，
 * 而 `--van-nav-bar-icon-color` 默认取 `--van-primary-color` = #1989fa），
 * 与搜索页那个 20px、`#323233` 的深灰箭头不是一套。这里用 `#left` 插槽把箭头换成
 * 与搜索页逐像素一致的自绘按钮；标题、右侧文字、底部发丝线仍由 van-nav-bar 负责，
 * 所以它是 van-nav-bar 的直接替代品，不是另起一个导航条。</p>
 *
 * <p>★ 按钮自己**不绑 click**。点击会冒泡到 Vant 的 `.van-nav-bar__left` 容器，
 * 由容器 emit `clickLeft`。这样两个入口都照旧生效：页面侧写的 `@click-left`，
 * 以及回归脚本里「直接点 `.van-nav-bar__left`」的写法
 * （`order-detail-back-verify` / `member-subpages-tabbar-verify` / `view-history-verify` 等）。
 * 反过来，如果按钮自己绑 click，一次点击会既触发按钮、又冒泡到容器 —— 返回执行两次。</p>
 *
 * <p>用法与 van-nav-bar 完全一致：`title` / `right-text` / `@click-left` / `@click-right`
 * 都靠属性透传落到根节点的 van-nav-bar 上。</p>
 *
 * <p>右侧要放**图标**（而不是文字）时用 `#right` 插槽：</p>
 *
 * <pre>&lt;PageNavBar title="浏览记录" @click-left="router.back()"&gt;
 *   &lt;template #right&gt;&lt;button …&gt;&lt;ClearIcon /&gt;&lt;/button&gt;&lt;/template&gt;
 * &lt;/PageNavBar&gt;</pre>
 */
</script>

<template>
  <van-nav-bar>
    <template #left>
      <button class="page-nav__back" type="button" aria-label="返回">
        <van-icon name="arrow-left" size="20" />
      </button>
    </template>
    <!-- 右侧插槽**条件透传**：只有调用方真的传了 #right 才提供。
         ★ 不能无条件提供 —— 一旦提供了 right 插槽，van-nav-bar 的默认内容
         （`{{ rightText }}`）就不再渲染，`right-text` 属性会静默失效。 -->
    <template v-if="$slots.right" #right>
      <slot name="right" />
    </template>
  </van-nav-bar>
</template>

<style scoped>
/* 与搜索页 `.search__back` 同一套：40×40 透明热区、20px 箭头、深灰 #323233。
   热区本身已提供足够的触摸空间，所以不额外留内边距。 */
.page-nav__back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  background: none;
  color: #323233;
  cursor: pointer;
}
/* 箭头色：压回深灰。Vant 的 `.van-nav-bar .van-icon`（0,2,0）会把图标染成主题蓝，
   本选择器带 scoped 属性选择器（0,3,0），权重更高。 */
.page-nav__back :deep(.van-icon) {
  color: #323233;
}
/* 左容器原本 `padding: 0 16px`。改成 2px 与搜索栏（`.search__bar` 的左内边距）对齐 ——
   40px 热区 + 2px 内边距下箭头中心落在 22px，与搜索页完全一致，且只比 Vant 原位置
   （16px 内边距 + 16px 图标 → 中心 24px）左移 2px，版面基本不动。
   若保留 16px 内边距，40px 热区会把箭头中心推到 36px，看起来会「离屏幕边太远」。 */
:deep(.van-nav-bar__left) {
  padding: 0 2px;
}
</style>
