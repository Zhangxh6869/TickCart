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

/**
 * 「回到顶部」图标（设计给的 `回到顶部.svg`：上方一条横线 + 下方上箭头）。
 *
 * 只保留 viewBox 与 path —— 源文件里的 `t` / `p-id` / `class` / DOCTYPE 都是导出工具
 * 留下的元数据，对渲染无意义；`fill` 从源文件的 `#2c2c2c` 改成 `currentColor`，
 * 跟随按钮的文字色（A 方案 = 品牌蓝），与 TabBarIcon / 详情页分享图标同一套做法。
 *
 * ★ viewBox 是**收紧过**的，不是文件自带的 `0 0 1024 1024`：图形只占原画布的
 *   69%×64%（居中，但偏小），沿用原画布会让它在 40px 圆钮里明显小一圈。
 *   这里的值 = 两段 path 的墨迹并集包围盒（量法见 `cdp/back-to-top-icon-measure.mjs`），
 *   收紧后 20×20 的图标盒里墨迹正好 20×18.5 居中 —— 与「收藏」图标同一处理
 *   （详情页分享图标没收紧，是因为它的墨迹本来就铺满 1024×921.6）。
 *   改 path 必须重新量一次并同步这个 viewBox。
 */
const TOP_ICON = {
  viewBox: '154.582 184.864 710.68 655.822',
  paths: [
    'M754.42175 535.630187 534.314727 315.041186c-3.136433-4.727675-7.768941-8.646937-13.719466-10.728342-3.183506-1.249457-6.563486-1.812275-9.993608-1.738597-0.24457 0-0.442068-0.12382-0.697895-0.12382-0.331551 0-0.600681 0.148379-0.932232 0.170892-3.342118-0.047072-6.638187 0.466628-9.711176 1.665942-6.05081 2.105964-10.751878 6.075369-13.912871 10.924817L265.432837 535.630187c-10.201339 10.191106-9.406231 27.53103 1.727341 38.653345 11.144828 11.118222 27.947516 11.412934 38.151925 1.223874 0.806365-0.832971 1.603521-1.372254 2.276856-2.303462l173.826977-173.521009 0.282433 413.372725c-0.159636 1.079588 0 1.959631 0 3.039219 0 13.568017 12.076036 24.591071 27.838022 24.591071s28.54615-11.023054 28.54615-24.591071l0.295735-416.411945 173.862793 173.521009c0.686638 0.931209 1.469468 1.470491 2.304486 2.303462 10.188037 10.18906 26.991748 9.894348 38.136575-1.223874C763.829004 563.161217 764.612856 545.821293 754.42175 535.630187z',
    'M865.262184 209.94764c0 13.851473-11.220552 25.082258-25.083282 25.082258L179.665451 235.029898c-13.852496 0-25.083282-11.231809-25.083282-25.082258l0 0c0-13.852496 11.230786-25.083282 25.083282-25.083282l660.513452 0C854.041632 184.864358 865.262184 196.095143 865.262184 209.94764L865.262184 209.94764z',
  ],
}

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
      <!-- 设计给的「回到顶部」图标（两段 path：横线 + 上箭头），
           与分享 / 收藏 / tabbar 图标同一套做法（只留 viewBox + path，fill=currentColor） -->
      <svg
        class="back-to-top__icon"
        :viewBox="TOP_ICON.viewBox"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
        focusable="false"
      >
        <path v-for="(d, i) in TOP_ICON.paths" :key="i" :d="d" fill="currentColor" />
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
