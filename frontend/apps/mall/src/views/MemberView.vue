<script setup lang="ts">
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

async function onLogout() {
  await showConfirmDialog({ title: '提示', message: '确认退出登录？' })
  await userStore.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="member">
    <div class="member__header">
      <van-image round width="60" height="60" :src="userStore.userInfo?.avatar || ''" />
      <div class="member__info">
        <div class="member__name">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</div>
        <div class="member__phone">{{ userStore.userInfo?.phone }}</div>
      </div>
    </div>

    <van-cell-group inset class="member__cells">
      <van-cell title="我的订单" is-link value="阶段2开通" />
      <van-cell title="收货地址" is-link value="阶段2开通" />
      <van-cell title="我的收藏" is-link value="阶段2开通" />
    </van-cell-group>

    <div class="member__logout">
      <van-button round block type="danger" @click="onLogout">退出登录</van-button>
    </div>
  </div>
</template>

<style scoped>
.member__header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 24px 20px;
  background: linear-gradient(135deg, #ee0a24, #ff6034);
  color: #fff;
}
.member__name {
  font-size: 18px;
  font-weight: 600;
}
.member__phone {
  font-size: 13px;
  opacity: 0.9;
  margin-top: 2px;
}
.member__cells {
  margin-top: 12px;
}
.member__logout {
  margin: 24px 16px;
}
</style>
