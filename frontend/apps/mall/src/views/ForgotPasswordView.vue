<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { SMS_SCENE, resetPassword, sendSmsCode } from '@/api/auth'

const router = useRouter()

const loading = ref(false)
const form = reactive({
  phone: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

/**
 * 回到登录页：正常是从登录页进来的，直接回退即可；
 * 直接打开 /forgot-password（无历史）时退回登录页 —— 与登录页自身的 goBack 同一套做法。
 */
function backToLogin() {
  if (window.history.state?.back) router.back()
  else router.replace({ name: 'login' })
}

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    showToast('请输入正确的手机号')
    return
  }
  // 场景必须与后端校验时一致（3 = 重置密码），否则「登录的码」也能拿来改密码
  await sendSmsCode(form.phone, SMS_SCENE.RESET_PASSWORD)
  showToast('验证码已发送（开发环境固定 123456）')
}

async function submit() {
  // 前端只做「填没填对」的即时反馈；长度/格式的最终口径在后端 DTO 的校验注解上
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    showToast('请输入正确的手机号')
    return
  }
  if (!form.code) {
    showToast('请输入验证码')
    return
  }
  if (form.newPassword.length < 6 || form.newPassword.length > 32) {
    showToast('密码长度需在 6~32 位')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    showToast('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    await resetPassword({
      phone: form.phone,
      code: form.code,
      newPassword: form.newPassword,
    })
    showToast('密码已重置，请用新密码登录')
    backToLogin()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="forgot">
    <button class="forgot__back" type="button" aria-label="返回" @click="backToLogin">
      <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
        <path
          d="M15 5 8 12l7 7"
          fill="none"
          stroke="currentColor"
          stroke-width="2.2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>

    <div class="forgot__brand">
      <span class="forgot__logo">TC</span>
      <span class="forgot__brand-name">TickCart</span>
    </div>

    <h1 class="forgot__title">重置密码</h1>
    <p class="forgot__subtitle">用注册手机号验证身份后设置新密码</p>

    <van-form class="forgot__form" @submit="submit">
      <label class="forgot__field">
        <span class="forgot__label">手机号</span>
        <input
          v-model="form.phone"
          class="forgot__input"
          type="tel"
          placeholder="请输入注册手机号"
          autocomplete="tel"
        />
      </label>

      <div class="forgot__code-row">
        <label class="forgot__field forgot__field--code">
          <span class="forgot__label">验证码</span>
          <input v-model="form.code" class="forgot__input" type="text" placeholder="请输入验证码" />
        </label>
        <button class="forgot__code-btn" type="button" @click.prevent="sendCode">获取验证码</button>
      </div>

      <label class="forgot__field">
        <span class="forgot__label">新密码</span>
        <input
          v-model="form.newPassword"
          class="forgot__input"
          type="password"
          placeholder="6~32 位"
          autocomplete="new-password"
        />
      </label>

      <label class="forgot__field">
        <span class="forgot__label">确认新密码</span>
        <input
          v-model="form.confirmPassword"
          class="forgot__input"
          type="password"
          placeholder="请再次输入新密码"
          autocomplete="new-password"
        />
      </label>

      <div class="forgot__submit">
        <van-button block type="danger" native-type="submit" :loading="loading">
          重置密码
        </van-button>
      </div>
    </van-form>

    <div class="forgot__links">
      <span class="forgot__link-text">想起来了？</span>
      <button class="forgot__link" type="button" @click="backToLogin">返回登录</button>
    </div>
  </div>
</template>

<style scoped>
/* 页面根：必须「不透明 + 至少一屏高」—— 从右侧推入时要完整盖住下面的登录页，
   半透明或不够高都会透出背后的内容。
   ★ 页面根类名 `.forgot` 在 App.vue 的常驻 `position: relative; z-index: 10` 规则里
   （横向推进同样需要它，否则过渡结束后会退化成普通块级元素，被还在 leave 中的登录页反盖）。
   ★ 但 `.forgot` **不在** App.vue 的 `:is(.login, .address)` 那组里 —— 那组只服务
     up/down 全屏层过渡；本页走 page-slide-left / right，改类名记得同步这两处。 */
.forgot {
  min-height: 100vh;
  padding: 16px 26px 24px;
  background: #fff;
}

/* ── 顶部返回 ────────────────────────────────────────────────────────── */
.forgot__back {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-left: -6px;
  padding: 0;
  border: none;
  background: none;
  color: #323233;
  cursor: pointer;
}

/* ── 品牌标识（与登录页同一套） ─────────────────────────────────────── */
.forgot__brand {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 30px;
}
.forgot__logo {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: rgb(107, 163, 200);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.forgot__brand-name {
  font-size: 15px;
  font-weight: 600;
  color: rgb(46, 107, 138);
  letter-spacing: 0.3px;
}

/* ── 标题区 ──────────────────────────────────────────────────────────── */
.forgot__title {
  margin-top: 32px;
  font-size: 24px;
  font-weight: 600;
  color: #323233;
  letter-spacing: 0.5px;
}
.forgot__subtitle {
  margin-top: 8px;
  font-size: 13px;
  color: #969799;
}

/* ── 输入区：与登录页一致的下划线式 ─────────────────────────────────── */
.forgot__form {
  margin-top: 34px;
}
.forgot__field {
  display: block;
  margin-top: 26px;
}
/* 第一个字段贴着表单上沿；用 > 限定直接子元素，验证码那个字段在 .forgot__code-row 里 */
.forgot__form > .forgot__field:first-child {
  margin-top: 0;
}
.forgot__label {
  display: block;
  font-size: 11px;
  color: #969799;
  letter-spacing: 0.5px;
}
.forgot__input {
  display: block;
  width: 100%;
  height: 38px;
  padding: 0;
  border: none;
  border-bottom: 1px solid #ebedf0;
  border-radius: 0;
  background: none;
  font-family: inherit;
  font-size: 14px;
  color: #323233;
  outline: none;
  transition: border-color 200ms;
}
.forgot__input::placeholder {
  color: #c8c9cc;
}
.forgot__input:focus {
  border-bottom-color: rgb(107, 163, 200);
}

/* 验证码一行：输入框占满剩余宽度，文字按钮与输入框底边对齐 */
.forgot__code-row {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  margin-top: 26px;
}
.forgot__code-row .forgot__field {
  flex: 1;
  min-width: 0;
  margin-top: 0;
}
.forgot__code-btn {
  flex: 0 0 auto;
  height: 38px;
  padding: 0;
  border: none;
  background: none;
  font-family: inherit;
  font-size: 13px;
  color: rgb(46, 107, 138);
  cursor: pointer;
}

/* ── 主按钮 ──────────────────────────────────────────────────────────── */
.forgot__submit {
  margin-top: 34px;
  /* 与登录页同一套：保留 type="danger" 语义，只覆盖配色为品牌蓝 */
  --van-button-danger-background: rgb(107, 163, 200);
  --van-button-danger-border-color: rgb(107, 163, 200);
}
.forgot__submit :deep(.van-button) {
  height: 48px;
  border-radius: 8px;
  font-weight: var(--van-font-bold, 600);
}

/* ── 底部文字链 ──────────────────────────────────────────────────────── */
.forgot__links {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 34px;
  padding-top: 18px;
  border-top: 0.5px solid #f2f3f5;
  font-size: 13px;
  color: #969799;
}
.forgot__link {
  padding: 0;
  border: none;
  background: none;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  color: rgb(46, 107, 138);
  cursor: pointer;
}
</style>
