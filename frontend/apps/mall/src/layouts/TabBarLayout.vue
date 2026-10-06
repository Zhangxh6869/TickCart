<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const active = computed(() => route.name as string)

/** 商品详情页自带底部操作栏（收藏 / 加购 / 立即购买），隐藏 tabbar 让位 */
const showTabbar = computed(() => route.name !== 'product-detail')

/** 登录后同步购物车角标；退出登录时清空本地状态 */
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      cartStore.refreshCount()
    } else {
      cartStore.reset()
    }
  },
  { immediate: true }
)

function go(name: string) {
  router.push({ name })
}
</script>

<template>
  <div class="layout" :class="{ 'layout--tabbar': showTabbar }">
    <div class="layout__content">
      <slot />
    </div>

    <van-tabbar v-if="showTabbar" :model-value="active" @change="go">
      <van-tabbar-item name="home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item name="cart" icon="cart-o" :badge="cartStore.badge">购物车</van-tabbar-item>
      <van-tabbar-item name="member" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
/* 有 tabbar 时给内容区留出底部空间，避免被固定定位的 tabbar 遮住 */
.layout--tabbar {
  padding-bottom: 50px;
}
</style>
