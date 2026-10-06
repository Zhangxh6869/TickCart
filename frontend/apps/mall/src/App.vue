<script setup lang="ts">
import { RouterView } from 'vue-router'
import TabBarLayout from '@/layouts/TabBarLayout.vue'
</script>

<template>
  <TabBarLayout>
    <RouterView />
  </TabBarLayout>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

:root {
  /* 页面背景色：同步覆盖 Vant 的页面背景变量，避免 van-image 等组件仍用旧的灰色底 */
  --van-background: rgb(255, 255, 255);
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', sans-serif;
  background: rgb(255, 255, 255);
  color: #323233;
}
</style>
