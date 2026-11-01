<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import DevCodeNotice from '@/components/DevCodeNotice.vue'
import { SMS_SCENE, fetchCaptcha, sendSmsCode, type CaptchaChallenge } from '@/api/auth'
import {
  PHONE_RE,
  hasFieldError,
  showsCode,
  showsPassword,
  showsPhone,
  showsUsername,
  validateLoginField,
  validateLoginForm,
  type LoginFormContext,
} from '@/utils/login-form'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const mode = ref<'login' | 'register'>('login')
const method = ref<'password' | 'sms'>('password')
const loading = ref(false)
const showPassword = ref(false)

const form = reactive({
  username: '',
  password: '',
  phone: '',
  code: '',
})

type FieldName = 'username' | 'password' | 'phone' | 'code'

/** 字段级错误：贴在对应输入框下方，比 toast 持久，也能指明是哪一项不对 */
const fieldErrors = reactive<Record<FieldName, string>>({
  username: '',
  password: '',
  phone: '',
  code: '',
})

/** 整体性错误：凭据错误、锁定提示、需要验证 */
const errorMsg = ref('')
/** 人机验证挑战；非空即表示当前处于「验证升级」态 */
const captcha = ref<CaptchaChallenge | null>(null)
const captchaCode = ref('')
/** 锁定剩余秒数，> 0 时禁用提交并逐秒递减 */
const retryLeft = ref(0)
let retryTimer: number | undefined
/** 验证码重发倒计时：与后端 tickcart.sms.guard.send-interval-seconds 保持一致 */
const codeLeft = ref(0)
let codeTimer: number | undefined
/** Mock 模式回显的验证码：非空即显示绿色悬浮窗（真实网关模式下恒为空） */
const devCode = ref('')

const isRegister = computed(() => mode.value === 'register')
const showMethodSwitch = computed(() => !isRegister.value)

const title = computed(() => (isRegister.value ? '创建账号' : '欢迎回来'))
const subtitle = computed(() =>
  isRegister.value ? '注册后即可下单、收藏与查看订单' : '登录后同步你的购物车与订单'
)

const submitLabel = computed(() => {
  if (retryLeft.value > 0) return `${retryLeft.value} 秒后重试`
  return isRegister.value ? '注册' : '登录'
})
const submitDisabled = computed(() => loading.value || retryLeft.value > 0)

// ── 校验规则 ──────────────────────────────────────────────────────────
// 规则本身在 `utils/login-form.ts`（纯函数，有单测）。这里只负责把响应式状态喂进去。
// ★ 关键约定：只校验「当前可见」的字段组 —— 短信登录时账密框不渲染、值为空是正常的，
//   若仍去校验它们，提交会被静默拦下（点登录毫无反应，且错误文案也在隐藏块里看不见）。

/** 校验上下文：字段可见性由 mode / method 共同决定 */
const formContext = computed<LoginFormContext>(() => ({
  method: method.value,
  mode: mode.value,
}))

/**
 * 字段可见性：唯一来源是 login-form.ts 的 `shows*`。
 *
 * <p>模板不再自己写 `v-if="method === 'sms' || isRegister"` 这类条件 ——
 * 校验规则与渲染条件一旦各写一份，就会重演「校验了看不见的字段」那个回归。</p>
 */
const visible = computed(() => ({
  username: showsUsername(formContext.value),
  phone: showsPhone(formContext.value),
  password: showsPassword(formContext.value),
  code: showsCode(formContext.value),
}))

function blurField(name: FieldName) {
  // 空字段还没输入就报错会很吵：只有填过内容、或已有错误时才即时校验
  const value = form[name]
  if (!value && !fieldErrors[name]) return
  fieldErrors[name] = validateLoginField(name, form, formContext.value)
}

function validateAll(): boolean {
  const errors = validateLoginForm(form, formContext.value)
  ;(Object.keys(errors) as FieldName[]).forEach((key) => {
    fieldErrors[key] = errors[key]
  })
  return !hasFieldError(errors)
}

function resetErrors() {
  errorMsg.value = ''
  ;(Object.keys(fieldErrors) as FieldName[]).forEach((key) => {
    fieldErrors[key] = ''
  })
}

// ── 交互 ──────────────────────────────────────────────────────────────

/** 独立全屏层的返回：能回退就回退，直接打开 /login 时退回首页（与商品详情页同一套做法） */
function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace({ name: 'home' })
}

function toggleMethod() {
  method.value = method.value === 'password' ? 'sms' : 'password'
  resetErrors()
}

function switchMode() {
  mode.value = isRegister.value ? 'login' : 'register'
  // 注册不展示登录方式切换：强制回到账密，避免从验证码登录切过来时残留 sms 状态
  if (isRegister.value) method.value = 'password'
  resetErrors()
}

async function sendCode() {
  if (codeLeft.value > 0) return
  if (!PHONE_RE.test(form.phone)) {
    fieldErrors.phone = '请输入正确的手机号'
    return
  }
  fieldErrors.phone = ''
  try {
    // 场景必须与后端校验时一致（1=注册 / 2=登录），否则拿注册的码去登录会被拒
    const result = await sendSmsCode(
      form.phone,
      mode.value === 'register' ? SMS_SCENE.REGISTER : SMS_SCENE.LOGIN
    )
    // 验证码每次随机；Mock 模式下后端把码回显在 devCode 里。
    // 有码就弹绿色悬浮窗（toast 一闪而过，6 位数字根本来不及抄），没有才退回普通提示。
    if (result?.devCode) {
      devCode.value = result.devCode
    } else {
      showToast('验证码已发送')
    }
    startCodeCountdown()
  } catch {
    // 限流时后端已经通过全局 toast 给出了「请 X 秒后重试」，这里只兜住未处理的拒绝
  }
}

function startCodeCountdown(seconds = 30) {
  codeLeft.value = seconds
  window.clearInterval(codeTimer)
  codeTimer = window.setInterval(() => {
    codeLeft.value -= 1
    if (codeLeft.value <= 0) {
      codeLeft.value = 0
      window.clearInterval(codeTimer)
    }
  }, 1000)
}

/** 刷新图形码：用户看不清或过期时点图重取 */
async function refreshCaptcha() {
  try {
    captcha.value = await fetchCaptcha()
    captchaCode.value = ''
  } catch {
    // 刷新失败保留旧图，不打断当前输入
  }
}

function startCountdown(seconds: number) {
  retryLeft.value = Math.max(1, Math.ceil(seconds))
  window.clearInterval(retryTimer)
  retryTimer = window.setInterval(() => {
    retryLeft.value -= 1
    if (retryLeft.value <= 0) {
      retryLeft.value = 0
      window.clearInterval(retryTimer)
    }
  }, 1000)
}

/**
 * 登录失败分流。
 *
 * 40101 = 需要人机验证（弹图形码）；42900 = 被锁定（倒计时）；其余按凭据错误处理。
 * 三种状态的界面完全不同，所以靠 code 分流而不是解析中文文案。
 */
function handleAuthError(error: unknown) {
  const response = (error as {
    response?: { data?: { code?: number; message?: string; data?: unknown } }
  })?.response
  const body = response?.data
  const code = body?.code

  if (code === 40101) {
    const challenge = body?.data as CaptchaChallenge | undefined
    if (challenge?.id) {
      captcha.value = challenge
      captchaCode.value = ''
    }
    errorMsg.value = body?.message || '请完成安全验证'
    return
  }

  if (code === 42900) {
    const payload = body?.data as { retryAfter?: number } | undefined
    startCountdown(payload?.retryAfter ?? 60)
    errorMsg.value = body?.message || '尝试过于频繁，请稍后再试'
    return
  }

  errorMsg.value = body?.message || (error as Error)?.message || '登录失败，请重试'
}

async function submit() {
  errorMsg.value = ''
  if (retryLeft.value > 0) return
  if (!validateAll()) return

  loading.value = true
  try {
    if (mode.value === 'login') {
      if (method.value === 'password') {
        await userStore.login({
          phone: form.phone,
          password: form.password,
          captchaId: captcha.value?.id,
          captchaCode: captcha.value ? captchaCode.value.trim() : undefined,
        })
      } else {
        await userStore.loginWithSms(form.phone, form.code)
      }
      showToast('登录成功')
    } else {
      // 注册走账密注册入口：四项必填，手机号 + 验证码一起保证账号真实性
      await userStore.register({
        username: form.username.trim(),
        phone: form.phone,
        password: form.password,
        code: form.code,
      })
      showToast('注册成功')
    }
    const redirect = (route.query.redirect as string) || '/'
    // 目标与当前地址相同就不跳 —— 避免一次冗余导航
    if (redirect !== route.fullPath) router.replace(redirect)
  } catch (error) {
    handleAuthError(error)
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  window.clearInterval(retryTimer)
  window.clearInterval(codeTimer)
})
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
      <!-- 用户名：仅注册。登录不再接受用户名，所以登录态这一行根本不渲染 -->
      <label v-if="visible.username" class="login__field">
        <span class="login__label">用户名</span>
        <input
          v-model="form.username"
          class="login__input"
          :class="{ 'login__input--error': fieldErrors.username }"
          type="text"
          placeholder="4~20 位字母、数字或下划线"
          autocomplete="username"
          @blur="blurField('username')"
        />
        <p v-if="fieldErrors.username" class="login__err">{{ fieldErrors.username }}</p>
      </label>

      <!-- 手机号：账密登录、验证码登录、注册三处共用，切换方式时保留已输入的值 -->
      <label v-if="visible.phone" class="login__field">
        <span class="login__label">手机号</span>
        <input
          v-model="form.phone"
          class="login__input"
          :class="{ 'login__input--error': fieldErrors.phone }"
          type="tel"
          inputmode="numeric"
          maxlength="11"
          placeholder="请输入手机号"
          autocomplete="tel"
          @blur="blurField('phone')"
        />
        <p v-if="fieldErrors.phone" class="login__err">{{ fieldErrors.phone }}</p>
      </label>

      <!-- 密码：账密登录 或 注册 -->
      <label v-if="visible.password" class="login__field">
        <span class="login__label">密码</span>
        <span class="login__input-wrap">
          <input
            v-model="form.password"
            class="login__input login__input--pwd"
            :class="{ 'login__input--error': fieldErrors.password }"
            :type="showPassword ? 'text' : 'password'"
            :placeholder="isRegister ? '8~32 位，需含字母和数字' : '请输入密码'"
            :autocomplete="isRegister ? 'new-password' : 'current-password'"
            @blur="blurField('password')"
          />
          <!-- 显隐切换：手机上输错密码的挫败感主要来自「看不见自己输了什么」 -->
          <button
            class="login__eye"
            type="button"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            @click.prevent="showPassword = !showPassword"
          >
            <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path
                v-if="!showPassword"
                d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6-10-6-10-6Z M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
              />
              <path
                v-else
                d="M4 4l16 16M9.9 5.2A9.9 9.9 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3.2 3.9M6.3 7.4A16.6 16.6 0 0 0 2 12s3.6 7 10 7c1 0 1.9-.2 2.8-.5"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
              />
            </svg>
          </button>
        </span>
        <p v-if="fieldErrors.password" class="login__err">{{ fieldErrors.password }}</p>
      </label>

      <!-- 验证码：验证码登录 或 注册 -->
      <template v-if="visible.code">
        <div class="login__code-row">
          <label class="login__field login__field--code">
            <span class="login__label">验证码</span>
            <input
              v-model="form.code"
              class="login__input"
              :class="{ 'login__input--error': fieldErrors.code }"
              type="text"
              inputmode="numeric"
              maxlength="6"
              placeholder="请输入验证码"
              @blur="blurField('code')"
            />
          </label>
          <button
            class="login__code-btn"
            type="button"
            :disabled="codeLeft > 0"
            @click.prevent="sendCode"
          >
            {{ codeLeft > 0 ? `${codeLeft}s 后重发` : '获取验证码' }}
          </button>
        </div>
        <p v-if="fieldErrors.code" class="login__err">{{ fieldErrors.code }}</p>
      </template>

      <!-- 人机验证：失败次数达到阈值后出现，验证通过才继续比对密码 -->
      <div v-if="captcha" class="login__field login__captcha">
        <span class="login__label">图形验证码</span>
        <div class="login__captcha-row">
          <input
            v-model="captchaCode"
            class="login__input"
            type="text"
            maxlength="6"
            placeholder="请输入图中字符"
            autocomplete="off"
          />
          <button
            class="login__captcha-img"
            type="button"
            aria-label="刷新图形验证码"
            @click.prevent="refreshCaptcha"
            v-html="captcha.svg"
          ></button>
        </div>
      </div>

      <div v-if="showMethodSwitch" class="login__switch">
        <button class="login__switch-btn" type="button" @click="toggleMethod">
          用{{ method === 'password' ? '验证码' : '账号密码' }}登录
        </button>
        <!-- 「忘记密码」只在账密登录时出现：验证码登录本来就不需要它 -->
        <button
          v-if="method === 'password'"
          class="login__switch-btn login__switch-btn--accent"
          type="button"
          @click="router.push({ name: 'forgot-password' })"
        >
          忘记密码？
        </button>
      </div>

      <!-- 整体性错误：凭据错误 / 锁定倒计时 / 需要验证 -->
      <p v-if="errorMsg" class="login__alert">{{ errorMsg }}</p>

      <div class="login__submit">
        <van-button
          block
          type="danger"
          native-type="submit"
          :loading="loading"
          :disabled="submitDisabled"
        >
          {{ submitLabel }}
        </van-button>
      </div>
    </van-form>

    <div class="login__links">
      <span class="login__link-text">{{ isRegister ? '已有账号？' : '还没有账号？' }}</span>
      <button class="login__link" type="button" @click="switchMode">
        {{ isRegister ? '去登录' : '立即注册' }}
      </button>
    </div>

    <!-- 开发环境验证码：绿色悬浮窗，升起 → 停留 → 缓缓上移淡出。
         :key 绑码值，重新获取时能重放动画（同码不会出现，这里只是兜底） -->
    <DevCodeNotice v-if="devCode" :key="devCode" :code="devCode" @done="devCode = ''" />
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
/* 校验失败：只改下划线颜色，不加边框和底色，保持这一版的留白气质 */
.login__input--error,
.login__input--error:focus {
  border-bottom-color: #c0392b;
}

/* 字段级错误文案 */
.login__err {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.4;
  color: #c0392b;
}

/* 密码显隐：输入框右侧的透明热区，不占视觉空间 */
.login__input-wrap {
  position: relative;
  display: block;
}
.login__input--pwd {
  padding-right: 34px;
}
.login__eye {
  position: absolute;
  top: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 38px;
  padding: 0;
  border: none;
  background: none;
  color: #969799;
  cursor: pointer;
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
/* 倒计时期间置灰：不可点也不消失，位置不变避免表单跳动 */
.login__code-btn:disabled {
  color: #c8c9cc;
  cursor: default;
}

/* ── 图形验证码 ─────────────────────────────────────────────────────── */
.login__captcha-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.login__captcha-row .login__input {
  flex: 1;
  min-width: 0;
}
.login__captcha-img {
  flex: 0 0 auto;
  width: 104px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: #f7f8fa;
  overflow: hidden;
  cursor: pointer;
}
.login__captcha-img :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
}

/* ── 整体性错误：锁定 / 凭据错误 / 需要验证 ─────────────────────────── */
.login__alert {
  margin-top: 18px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fdf3f2;
  font-size: 12.5px;
  line-height: 1.5;
  color: #c0392b;
}

/* ── 登录方式切换 / 忘记密码：一行两端 ──────────────────────────────── */
.login__switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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
/* 「忘记密码？」用品牌深蓝，与左侧的「用验证码登录」拉开主次 */
.login__switch-btn--accent {
  color: rgb(46, 107, 138);
}

/* ── 主按钮 ──────────────────────────────────────────────────────────── */
.login__submit {
  margin-top: 34px;
  /* 主按钮改用品牌蓝：保留 type="danger" 的语义（不改组件用法），
     只覆盖 Vant danger 的两个配色变量 —— 与「我的」页「退出登录」同一套做法。
     原来是 Vant 默认红 #ee0a24，是全站唯一一处红，与钢蓝品牌色冲突。 */
  --van-button-danger-background: rgb(107, 163, 200);
  --van-button-danger-border-color: rgb(107, 163, 200);
  /* 锁定态下 Vant 会把按钮变灰，这里保持「灰但可读」 */
  --van-button-danger-disabled-background: #c9d6de;
  --van-button-danger-disabled-border-color: #c9d6de;
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
