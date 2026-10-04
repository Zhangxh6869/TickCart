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

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', sans-serif;
  background: #f7f8fa;
  color: #323233;
}
</style>
