<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const active = computed(() => route.name as string)

function go(name: string) {
  router.push({ name })
}
</script>

<template>
  <div class="layout">
    <div class="layout__content">
      <slot />
    </div>

    <van-tabbar :model-value="active" @change="go">
      <van-tabbar-item name="home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item name="product-list" icon="apps-o">分类</van-tabbar-item>
      <van-tabbar-item name="member" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  padding-bottom: 50px;
}
</style>
