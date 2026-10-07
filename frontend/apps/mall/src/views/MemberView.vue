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
    <!-- 登录界面 ↔ 个人中心：mode="out-in" 串行播放，两段都是文档流里的普通块，不会叠在一起。
         原先这里是硬切（v-if 直接换内容），与项目里其它页面都有过渡对比很割裂。 -->
    <Transition name="auth-swap" mode="out-in">
      <!-- 未登录：直接在本页展示登录界面（不跳转到 /login），登录成功后留在「我的」 -->
      <LoginView v-if="!userStore.isLoggedIn" key="login" embedded redirect="/member" />

      <div v-else key="member" class="member">
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
    </Transition>
  </div>
</template>

<style scoped>
/* ── 登录界面 ↔ 个人中心 的切换 ──────────────────────────────────────
   容器很矮（未登录时只有一张登录表单那么高），做「整屏上滑」没有意义，
   所以用「旧内容上移淡出、新内容从下方淡入」表达同一套上滑语义，与登录页的
   page-slide-up 是同一个方向感。
   位移用 top + position: relative，**不用 transform** —— 本项目页面内一律不引入
   transform（会让 fixed 后代改成相对页面定位）。position: relative 的 top 初值是 auto
   不可插值，所以基线 top: 0 必须在 -active 里显式写出来。 */
.auth-swap-enter-active {
  position: relative;
  top: 0;
  transition: top 180ms ease-out, opacity 180ms ease-out;
}
.auth-swap-leave-active {
  position: relative;
  top: 0;
  transition: top 180ms ease-in, opacity 180ms ease-in;
}
.auth-swap-enter-from {
  top: 16px;
  opacity: 0;
}
.auth-swap-leave-to {
  top: -10px;
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .auth-swap-enter-active,
  .auth-swap-leave-active {
    transition-duration: 1ms;
  }
}

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
