<script setup lang="ts">
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { useUserStore } from '@/stores/user'
import LoginView from '@/views/LoginView.vue'

const router = useRouter()
const userStore = useUserStore()

async function onLogout() {
  await showConfirmDialog({ title: '提示', message: '确认退出登录？' })
  await userStore.logout()
  // 本页未登录态就是登录界面，退出后无需跳转：登录态一变，模板自动切回登录表单
}
</script>

<template>
  <!-- 根节点必须**恒为同一个元素**：App.vue 的 <Transition> 要求单根，
       且根元素类型一旦变化会被它当成「换页」而套上当前页面过渡的类名
       （实测：退出登录后登录表单被停在 left:100%，整页看起来是空白）。
       所以这里用一层固定 div 包住，登录态切换只换它内部的子节点。 -->
  <div class="member-page">
    <!-- 未登录：直接在本页展示登录界面（不跳转到 /login），登录成功后留在「我的」 -->
    <LoginView v-if="!userStore.isLoggedIn" redirect="/member" />

    <div v-else class="member">
      <div class="member__header">
        <van-image round width="60" height="60" :src="userStore.userInfo?.avatar || ''" />
        <div class="member__info">
          <div class="member__name">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</div>
          <div class="member__phone">{{ userStore.userInfo?.phone }}</div>
        </div>
      </div>

      <van-cell-group inset class="member__cells">
        <van-cell title="我的订单" is-link @click="router.push({ name: 'orders' })" />
        <van-cell title="收货地址" is-link @click="router.push({ name: 'address' })" />
        <van-cell title="我的收藏" is-link value="阶段2开通" />
      </van-cell-group>

      <div class="member__logout">
        <van-button round block type="danger" @click="onLogout">退出登录</van-button>
      </div>
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
