<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import { sendSmsCode } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/**
 * 登录成功后的去处。
 * 由调用方显式指定时优先（如 MemberView 内嵌本组件时传 "/member"，登录后就地变成个人中心）；
 * 否则用路由 query.redirect（守卫重定向、详情页「需要登录」跳转都会带上）；最后回首页。
 */
const props = defineProps<{ redirect?: string }>()

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
    const redirect = props.redirect || (route.query.redirect as string) || '/'
    // 目标与当前地址相同就不跳（内嵌在 /member 时即此场景）——避免一次冗余导航
    if (redirect !== route.fullPath) router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <van-nav-bar :title="mode === 'login' ? '登录' : '注册'" />

    <div class="login__switch">
      <span :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</span>
      <span :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</span>
    </div>

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

    <div v-if="mode === 'login'" class="login__method" @click="method = method === 'password' ? 'sms' : 'password'">
      切换为{{ method === 'password' ? '手机验证码' : '账密' }}登录
    </div>
  </div>
</template>

<style scoped>
.login__switch {
  display: flex;
  justify-content: center;
  gap: 32px;
  padding: 20px 0;
  font-size: 16px;
}
.login__switch span {
  color: #969799;
  padding-bottom: 6px;
}
.login__switch span.active {
  color: #323233;
  font-weight: 600;
  border-bottom: 2px solid #ee0a24;
}
.login__submit {
  margin: 20px 16px;
}
.login__method {
  text-align: center;
  color: #1989fa;
  font-size: 14px;
  margin-top: 12px;
  cursor: pointer;
}
</style>
