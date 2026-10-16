<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import PageNavBar from '@/components/PageNavBar.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const info = computed(() => userStore.userInfo)

/** 性别 0未知 1男 2女 */
const genderText = computed(() => {
  const map: Record<number, string> = { 1: '男', 2: '女' }
  return map[info.value?.gender ?? 0] ?? '保密'
})

/** 角色 0普通用户 1管理员 */
const roleText = computed(() => (info.value?.role === 1 ? '管理员' : '普通用户'))
</script>

<template>
  <div class="profile">
    <PageNavBar title="个人信息" @click-left="router.back()" />

    <!-- 头部：头像 + 昵称，与「我的」页同一套视觉语言 -->
    <div class="profile__header">
      <van-image
        v-if="info?.avatar"
        round
        width="64"
        height="64"
        :src="info.avatar"
        class="profile__avatar"
      />
      <div v-else class="profile__avatar profile__avatar--empty">
        <van-icon name="user-o" size="30" />
      </div>
      <div class="profile__name">{{ info?.nickname || info?.username || '-' }}</div>
    </div>

    <van-cell-group inset class="profile__cells">
      <van-cell title="用户名" :value="info?.username || '-'" />
      <van-cell title="昵称" :value="info?.nickname || '未设置'" />
      <!-- 手机号由后端脱敏后返回（138****0000），前端不再二次处理 -->
      <van-cell title="手机号" :value="info?.phone || '未绑定'" />
      <van-cell title="性别" :value="genderText" />
      <van-cell title="账号角色" :value="roleText" />
      <van-cell title="用户ID" :value="info ? String(info.id) : '-'" />
    </van-cell-group>

    <p class="profile__tip">如需修改资料，请联系客服</p>
  </div>
</template>

<style scoped>
.profile {
  min-height: 100%;
  background: #f7f8fa;
}
.profile__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 24px 20px 20px;
  background: linear-gradient(
    to right,
    rgb(46, 107, 138) 0%,
    rgb(107, 163, 200) 50%,
    rgb(184, 212, 232) 100%
  );
}
.profile__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #fff;
  background: rgba(255, 255, 255, 0.28);
  border-radius: 50%;
}
.profile__name {
  font-size: 17px;
  font-weight: 600;
  color: #fff;
}
.profile__cells {
  margin-top: 12px;
}
.profile__tip {
  margin: 16px 0 0;
  font-size: 12px;
  color: #969799;
  text-align: center;
}
</style>
