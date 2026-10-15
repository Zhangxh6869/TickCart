<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
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

const isRegister = computed(() => mode.value === 'register')

/** 页面标题：一处只说一件事。
 *  原先 nav bar 写「登录」、下面又有一行「登录 / 注册」切换，两处说同一件事；
 *  现在标题只由 mode 决定，nav bar 让位给极简的「Logo + 品牌名」一行（见 .login__brand）。 */
const title = computed(() => (isRegister.value ? '创建账号' : '欢迎回来'))
const subtitle = computed(() =>
  isRegister.value ? '注册后即可下单、收藏与查看订单' : '登录后同步你的购物车与订单'
)

/** 只有登录态有「账密 / 验证码」两种方式；注册态一次收齐全部字段，不做切换 */
const showMethodSwitch = computed(() => !isRegister.value)

/** 独立全屏层的返回：能回退就回退，直接打开 /login 时退回首页（与商品详情页同一套做法） */
function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace({ name: 'home' })
}

/** 账密 ↔ 验证码互换 */
function toggleMethod() {
  method.value = method.value === 'password' ? 'sms' : 'password'
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
    <!-- 返回箭头自绘而非 van-nav-bar：这一版走极简留白，页面顶部只留一个深色箭头，
         用 Vant 默认导航条会多出一条分隔线和一条标题。 -->
    <button class="login__back" type="button" aria-label="返回" @click="goBack">
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

    <!-- 品牌标识：小方块 Logo + 品牌名，克制到只占一行 -->
    <div class="login__brand">
      <span class="login__logo">TC</span>
      <span class="login__brand-name">TickCart</span>
    </div>

    <h1 class="login__title">{{ title }}</h1>
    <p class="login__subtitle">{{ subtitle }}</p>

    <van-form class="login__form" @submit="submit">
      <template v-if="method === 'password' || isRegister">
        <label class="login__field">
          <span class="login__label">用户名</span>
          <input
            v-model="form.username"
            class="login__input"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </label>
        <label class="login__field">
          <span class="login__label">密码</span>
          <input
            v-model="form.password"
            class="login__input"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </label>
        <label v-if="isRegister" class="login__field">
          <span class="login__label">昵称</span>
          <input v-model="form.nickname" class="login__input" type="text" placeholder="选填" />
        </label>
      </template>

      <template v-if="method === 'sms' || isRegister">
        <label class="login__field">
          <span class="login__label">手机号</span>
          <input
            v-model="form.phone"
            class="login__input"
            type="tel"
            placeholder="请输入手机号"
            autocomplete="tel"
          />
        </label>
        <div class="login__code-row">
          <label class="login__field login__field--code">
            <span class="login__label">验证码</span>
            <input
              v-model="form.code"
              class="login__input"
              type="text"
              placeholder="请输入验证码"
            />
          </label>
          <button class="login__code-btn" type="button" @click.prevent="sendCode">
            获取验证码
          </button>
        </div>
      </template>

      <div v-if="showMethodSwitch" class="login__switch">
        <button class="login__switch-btn" type="button" @click="toggleMethod">
          用{{ method === 'password' ? '验证码' : '账号密码' }}登录
        </button>
      </div>

      <div class="login__submit">
        <van-button block type="danger" native-type="submit" :loading="loading">
          {{ isRegister ? '注册' : '登录' }}
        </van-button>
      </div>
    </van-form>

    <div class="login__links">
      <span class="login__link-text">{{ isRegister ? '已有账号？' : '还没有账号？' }}</span>
      <button class="login__link" type="button" @click="mode = isRegister ? 'login' : 'register'">
        {{ isRegister ? '去登录' : '立即注册' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 独立全屏层：必须「不透明 + 至少一屏高」—— 从底部滑上来时要盖住下面那一页，
   半透明或不够高都会透出背后的内容（这是上滑过渡能成立的前提）。 */
.login {
  min-height: 100vh;
  padding: 16px 26px 24px;
  background: #fff;
}

/* ── 顶部返回 ────────────────────────────────────────────────────────── */
.login__back {
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

/* ── 品牌标识：一行，不抢标题的视觉重心 ─────────────────────────────── */
.login__brand {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 30px;
}
.login__logo {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  /* 品牌浅蓝，与「结算」「退出登录」「购物车去登录」同色 */
  background: rgb(107, 163, 200);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.login__brand-name {
  font-size: 15px;
  font-weight: 600;
  color: rgb(46, 107, 138);
  letter-spacing: 0.3px;
}

/* ── 标题区 ──────────────────────────────────────────────────────────── */
.login__title {
  margin-top: 32px;
  font-size: 24px;
  font-weight: 600;
  color: #323233;
  letter-spacing: 0.5px;
}
.login__subtitle {
  margin-top: 8px;
  font-size: 13px;
  color: #969799;
}

/* ── 输入区：下划线式，去掉 Vant 灰底 / 边框 ───────────────────────── */
.login__form {
  margin-top: 34px;
}
.login__field {
  display: block;
  margin-top: 26px;
}
/* 第一个字段贴着表单上沿（.login__form 的 margin-top 已经是它和副标题的间距）；
   用 > 限定直接子元素，验证码那个字段在 .login__code-row 里，不受这条影响 */
.login__form > .login__field:first-child {
  margin-top: 0;
}
.login__label {
  display: block;
  font-size: 11px;
  color: #969799;
  letter-spacing: 0.5px;
}
.login__input {
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
.login__input::placeholder {
  color: #c8c9cc;
}
/* 聚焦时下划线转品牌色 —— 极简版里这是唯一的交互反馈 */
.login__input:focus {
  border-bottom-color: rgb(107, 163, 200);
}

/* 验证码一行：输入框占满剩余宽度，文字按钮与输入框底边对齐 */
.login__code-row {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  margin-top: 26px;
}
.login__code-row .login__field {
  flex: 1;
  min-width: 0;
  margin-top: 0;
}
.login__code-btn {
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

/* ── 登录方式切换：一行文字链，取代原先表单下方的蓝色按钮式链接 ────── */
.login__switch {
  margin-top: 18px;
}
.login__switch-btn {
  padding: 0;
  border: none;
  background: none;
  font-family: inherit;
  font-size: 12px;
  color: #646566;
  cursor: pointer;
}

/* ── 主按钮 ──────────────────────────────────────────────────────────── */
.login__submit {
  margin-top: 34px;
  /* 主按钮改用品牌蓝：保留 type="danger" 的语义（不改组件用法），
     只覆盖 Vant danger 的两个配色变量 —— 与「我的」页「退出登录」同一套做法。
     原来是 Vant 默认红 #ee0a24，是全站唯一一处红，与钢蓝品牌色冲突。 */
  --van-button-danger-background: rgb(107, 163, 200);
  --van-button-danger-border-color: rgb(107, 163, 200);
}
/* 极简版用 8px 直角（不用 Vant 的 round 胶囊）；
   字重与「结算」按钮对齐 —— 普通 van-button 只有 400，submit-bar 是 600。
   用 Vant 变量而不是写死 600，以后改 --van-font-bold 两处会一起变。 */
.login__submit :deep(.van-button) {
  height: 48px;
  border-radius: 8px;
  font-weight: var(--van-font-bold, 600);
}

/* ── 底部文字链 ──────────────────────────────────────────────────────── */
.login__links {
  display: flex;
  justify-content: center;
  gap: 4px;
  /* 紧跟主按钮、不推到屏幕底 —— 实测贴底会在按钮和它之间留出约 300px 的空白带，
     整页看着没写完；跟在按钮下方时多出来的留白落在页面末尾，读起来才是正常的收尾 */
  margin-top: 34px;
  padding-top: 18px;
  border-top: 0.5px solid #f2f3f5;
  font-size: 13px;
  color: #969799;
}
.login__link {
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
