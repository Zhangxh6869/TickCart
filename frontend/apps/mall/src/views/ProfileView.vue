<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showLoadingToast, showToast } from 'vant'
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

// ---------------------------------------------------------------------------
// 头像上传
// ---------------------------------------------------------------------------
const avatarInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

function triggerAvatar() {
  avatarInput.value?.click()
}

async function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  uploading.value = true
  showLoadingToast({ message: '上传中...', forbidClick: true, duration: 0 })
  try {
    await userStore.uploadAvatar(file)
    showToast('头像已更新')
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    uploading.value = false
    closeToast()
    // 清空，保证连续选择同一个文件也能再次触发 change
    input.value = ''
  }
}

// ---------------------------------------------------------------------------
// 修改用户名
// ---------------------------------------------------------------------------
const showNameDialog = ref(false)
const nameDraft = ref('')

function openNameDialog() {
  nameDraft.value = info.value?.username ?? ''
  showNameDialog.value = true
}

/** 与后端 ProfileUpdateRequest 的约束保持一致，先在本地拦一次，减少一次往返。 */
function validateName(username: string): string | null {
  if (!username) return '用户名不能为空'
  if (username.length < 4 || username.length > 20) return '用户名长度需在 4~20 位'
  if (!/^[a-zA-Z0-9_]+$/.test(username)) return '只能包含字母、数字、下划线'
  return null
}

/**
 * van-dialog 关闭前钩子：确认时先校验再保存。
 *
 * 校验不通过或保存失败时返回 false，弹窗保持打开，用户不必重新输入。
 */
async function onNameBeforeClose(action: string): Promise<boolean> {
  if (action !== 'confirm') return true

  const username = nameDraft.value.trim()
  const error = validateName(username)
  if (error) {
    showToast(error)
    return false
  }

  try {
    await userStore.updateProfile({ username, gender: info.value?.gender ?? 0 })
    showToast('已保存')
    return true
  } catch {
    return false
  }
}

// ---------------------------------------------------------------------------
// 修改性别
// ---------------------------------------------------------------------------
const showGenderSheet = ref(false)
const genderDraft = ref<number[]>([0])
const genderOptions = [
  { text: '保密', value: 0 },
  { text: '男', value: 1 },
  { text: '女', value: 2 },
]

function openGenderSheet() {
  genderDraft.value = [info.value?.gender ?? 0]
  showGenderSheet.value = true
}

async function confirmGender() {
  const gender = genderDraft.value[0] ?? 0
  try {
    await userStore.updateProfile({ username: info.value?.username ?? '', gender })
    showToast('已保存')
    showGenderSheet.value = false
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}
</script>

<template>
  <div class="profile">
    <PageNavBar title="个人信息" @click-left="router.back()" />

    <!-- 头部：头像 + 昵称，与「我的」页同一套视觉语言 -->
    <div class="profile__header">
      <button
        class="profile__avatar-btn"
        type="button"
        :disabled="uploading"
        aria-label="更换头像"
        @click="triggerAvatar"
      >
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
        <span class="profile__avatar-edit">更换头像</span>
      </button>
      <div class="profile__name">{{ info?.username || '-' }}</div>
      <input
        ref="avatarInput"
        class="profile__file"
        type="file"
        accept="image/*"
        @change="onAvatarChange"
      />
    </div>

    <van-cell-group inset class="profile__cells">
      <!-- 昵称与用户名自 V24 起全库统一（注册时 nickname = username），
           两者必然同值，故只保留「用户名」这一行，避免同一字符串显示两遍 -->
      <van-cell title="用户名" :value="info?.username || '-'" is-link @click="openNameDialog" />
      <!-- 手机号由后端脱敏后返回（138****0000），前端不再二次处理 -->
      <van-cell title="手机号" :value="info?.phone || '未绑定'" />
      <van-cell title="性别" :value="genderText" is-link @click="openGenderSheet" />
      <van-cell title="账号角色" :value="roleText" />
      <van-cell title="用户ID" :value="info ? String(info.id) : '-'" />
    </van-cell-group>

    <!-- 改用户名 -->
    <van-dialog
      v-model:show="showNameDialog"
      title="修改用户名"
      show-cancel-button
      :before-close="onNameBeforeClose"
    >
      <div class="profile__dialog">
        <van-field v-model="nameDraft" placeholder="4~20 位字母、数字或下划线" maxlength="20" />
        <p class="profile__dialog-tip">昵称会与用户名保持一致</p>
      </div>
    </van-dialog>

    <!-- 改性别 -->
    <van-popup v-model:show="showGenderSheet" position="bottom" round>
      <van-picker
        v-model="genderDraft"
        :columns="genderOptions"
        title="选择性别"
        @confirm="confirmGender"
        @cancel="showGenderSheet = false"
      />
    </van-popup>
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
.profile__avatar-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 0;
  color: inherit;
  cursor: pointer;
  background: none;
  border: none;
}
.profile__avatar-btn:disabled {
  cursor: default;
  opacity: 0.6;
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
.profile__avatar-edit {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
}
.profile__name {
  font-size: 17px;
  font-weight: 600;
  color: #fff;
}
.profile__file {
  display: none;
}
.profile__cells {
  margin-top: 12px;
}
.profile__dialog {
  padding: 16px 16px 8px;
}
.profile__dialog-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: #969799;
}
</style>
