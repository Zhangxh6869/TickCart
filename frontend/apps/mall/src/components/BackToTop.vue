<script setup lang="ts">
/**
 * 「回到顶部」浮动按钮（首页 / 搜索页共用）。
 *
 * 两个页面的滚动都发生在 **window** 上（都不是内部滚动容器），所以直接用 window.scrollY 判断。
 *
 * ★ 挂载位置：必须是页面根的**直接子元素**。首页 `.section` 带 `overflow: clip`，
 *   按钮放它里面会被裁掉；而 fixed 的包含块是视口，页面根的 relative / left 位移
 *   （tab 过渡就是这套写法）都不影响它。
 */
import { onActivated, onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 滚动超过该距离（px）才显示按钮。改它要同步 .workbuddy-ai/tmp/cdp/back-to-top-verify.mjs */
    threshold?: number
    /** 按钮距视口底部的距离（CSS 长度）—— 用于避开 tabbar 等固定底栏 */
    bottom?: string
  }>(),
  { threshold: 600, bottom: '62px' }
)

const visible = ref(false)
/** 系统「减弱动态效果」时，smooth 滚动降级为瞬时 */
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)')

function sync() {
  // 只在**跨越阈值**时改变量：scroll 高频触发，逐帧赋值 ref 会白跑渲染
  const next = window.scrollY > props.threshold
  if (next !== visible.value) visible.value = next
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: reduceMotion.matches ? 'auto' : 'smooth' })
}

onMounted(() => window.addEventListener('scroll', sync, { passive: true }))
// 返回本页时浏览器恢复滚动位置但不触发 scroll 事件（HomeView 校正吸顶层叠是同一条套路），补一次同步
onActivated(sync)
onBeforeUnmount(() => window.removeEventListener('scroll', sync))
</script>

<template>
  <Transition name="back-to-top">
    <button
      v-show="visible"
      class="back-to-top"
      type="button"
      :style="{ bottom: props.bottom }"
      aria-label="回到顶部"
      @click="scrollToTop"
    >
      <!-- 内联 SVG 上箭头，与分享图标 / tabbar 图标同一套做法（fill=currentColor 跟随按钮颜色） -->
      <svg
        class="back-to-top__icon"
        viewBox="0 0 24 24"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
        focusable="false"
      >
        <path d="M12 4.5 19 11.5h-4v8.5h-6v-8.5h-4L12 4.5z" fill="currentColor" />
      </svg>
    </button>
  </Transition>
</template>

<style scoped>
.back-to-top {
  position: fixed;
  right: 16px;
  /* bottom 由父页面通过 prop 传入：首页要避开 50px 的 tabbar，搜索页无 tabbar 贴 16px */
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  /* A 方案：白底 + 品牌蓝箭头。色值与首页 / 搜索页搜索框描边同源（rgb(107,163,200)） */
  background: #fff;
  color: rgb(107, 163, 200);
  /* 轻投影：按钮常悬在商品卡片之上，没影子会直接糊进卡片里 */
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  cursor: pointer;
  /* 高于吸顶区（z-index: 2）与 tabbar（z-index: 1）；位置上已避开 tabbar，这里只是兜底 */
  z-index: 5;
  transition:
    opacity 0.2s ease-out,
    transform 0.2s ease-out;
}
.back-to-top__icon {
  display: block;
  width: 20px;
  height: 20px;
}
.back-to-top-enter-active,
.back-to-top-leave-active {
  transition:
    opacity 0.2s ease-out,
    transform 0.2s ease-out;
}
.back-to-top-enter-from,
.back-to-top-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
/* 系统「减弱动态效果」时过渡压到接近瞬时，只保留状态切换 */
@media (prefers-reduced-motion: reduce) {
  .back-to-top-enter-active,
  .back-to-top-leave-active {
    transition-duration: 1ms;
  }
}
</style>
