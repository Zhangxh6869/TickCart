<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import { sendSmsCode } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const mode = ref<'login' | 'register'>('login')
const method = ref<'password' | 'sms'>('password')
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  phone: '',
  code: '',
  nickname: '',
})

/** 独立全屏层的返回：能回退就回退，直接打开 /login 时退回首页（与商品详情页同一套做法） */
function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace({ name: 'home' })
}

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    showToast('请输入正确的手机号')
    return
  }
  await sendSmsCode(form.phone, mode.value === 'register' ? 1 : 2)
  showToast('验证码已发送（开发环境固定 123456）')
}

async function submit() {
  loading.value = true
  try {
    if (mode.value === 'login') {
      if (method.value === 'password') {
        await userStore.login(form.username, form.password)
      } else {
        await userStore.loginWithSms(form.phone, form.code)
      }
      showToast('登录成功')
    } else {
      await userStore.register({
        username: form.username,
        password: form.password,
        phone: form.phone || undefined,
        nickname: form.nickname || undefined,
      })
      showToast('注册成功')
    }
    const redirect = (route.query.redirect as string) || '/'
    // 目标与当前地址相同就不跳 —— 避免一次冗余导航
    if (redirect !== route.fullPath) router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <!-- 独立全屏层：左返回 + **唯一**标题。
         原先 nav bar 写「登录」、下面又有一行「登录 / 注册」切换，两处说同一件事；
         现在标题跟着 mode 走，注册入口挪到表单下方的文字链（见 .login__links）。 -->
    <van-nav-bar :title="mode === 'login' ? '登录' : '注册'" left-arrow @click-left="goBack" />

    <van-form @submit="submit">
      <template v-if="method === 'password' || mode === 'register'">
        <van-cell-group inset>
          <van-field v-model="form.username" label="用户名" placeholder="请输入用户名" />
          <van-field v-model="form.password" type="password" label="密码" placeholder="请输入密码" />
          <van-field
            v-if="mode === 'register'"
            v-model="form.nickname"
            label="昵称"
            placeholder="选填"
          />
        </van-cell-group>
      </template>

      <template v-if="method === 'sms' || mode === 'register'">
        <van-cell-group inset>
          <van-field v-model="form.phone" label="手机号" placeholder="请输入手机号" />
          <van-field v-model="form.code" center label="验证码" placeholder="请输入验证码">
            <template #button>
              <van-button size="small" type="primary" @click.prevent="sendCode">获取验证码</van-button>
            </template>
          </van-field>
        </van-cell-group>
      </template>

      <div class="login__submit">
        <van-button round block type="danger" native-type="submit" :loading="loading">
          {{ mode === 'login' ? '登录' : '注册' }}
        </van-button>
      </div>
    </van-form>

    <div class="login__links">
      <button
        v-if="mode === 'login'"
        class="login__link"
        type="button"
        @click="method = method === 'password' ? 'sms' : 'password'"
      >
        切换为{{ method === 'password' ? '手机验证码' : '账密' }}登录
      </button>
      <button
        class="login__link"
        type="button"
        @click="mode = mode === 'login' ? 'register' : 'login'"
      >
        {{ mode === 'login' ? '还没有账号？立即注册' : '已有账号？去登录' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 独立全屏层：必须是「不透明 + 至少一屏高」—— 从底部滑上来时要盖住下面那一页，
   半透明或不够高都会透出背后的内容（这是上滑过渡能成立的前提）。 */
.login {
  min-height: 100vh;
  background: #fff;
}

.login__submit {
  margin: 20px 16px;
}
.login__links {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}
.login__link {
  padding: 0;
  border: none;
  background: none;
  font-size: 14px;
  color: #1989fa;
  cursor: pointer;
}
</style>
