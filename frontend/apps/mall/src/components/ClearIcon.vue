<script setup lang="ts">
/**
 * 「清空」图标（浏览记录页右上角）。
 *
 * 图形来自设计稿交付的 SVG（iconfont 导出，viewBox 0 0 1024 1024），
 * path 的 d 值**逐字符照抄**，没有做任何简化或重绘 —— 与 OrderStatusIcon 同一套规矩，
 * 别手动改数字（踩过的坑：详情页的分享 / 收藏图标曾因手改 d 与设计稿对不上）。
 *
 * 颜色走 currentColor：设计稿里是 #2c2c2c 的纯色填充，这里不写死在 path 上，
 * 由使用方（.view-history__clear）决定，以后要随主题或状态变色不用动本文件。
 */
</script>

<template>
  <svg
    class="clear-icon"
    viewBox="0 0 1024 1024"
    fill="currentColor"
    aria-hidden="true"
    focusable="false"
  >
    <path d="M687.6 96.4H336.4v91.2h351.1V96.4zM636.7 398v405.5h-73.9V398h73.9z m-175.5 0v405.5h-73.9V398h73.9z m332.1-119.2H230.7l27.9 648.8h506.7l28-648.8zM696.8 5.1c40.4 0 73.3 35.6 73.9 79.8v102.7h147.8c20.2 0 36.6 17.8 37 39.9v41.2c0 5.5-4 10-9 10.1h-70.1L848 941.6c-1.8 42.9-33.7 76.6-72.6 77.3H249.8c-39 0-71.3-33.4-73.7-76l-0.1-1.3-28.5-662.7H77.7c-5 0-9.1-4.4-9.2-9.8v-40.9c0-22.2 16.2-40.2 36.3-40.5h148.4V86.2c0-44.3 32.5-80.4 72.7-81.1h370.9z" />
  </svg>
</template>

<style scoped>
/* 默认 20px：与顶部导航条里文字的视觉重量相当；使用方可覆盖 width/height */
.clear-icon {
  display: block;
  width: 20px;
  height: 20px;
}
</style>
