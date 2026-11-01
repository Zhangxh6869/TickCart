/**
 * 登录 / 注册表单校验（纯函数，不依赖 Vue 与 DOM）。
 *
 * <p><b>为什么单独抽出来</b>：这套规则原本写在 `LoginView.vue` 的 `&lt;script setup&gt;` 里，
 * 单测碰不到组件内部，于是「短信登录时账密字段被隐藏、却仍被校验」这个回归一路溜到了线上
 * （点登录毫无反应，见 `tests/login-form.spec.ts` 的回归用例）。规则挪到纯函数后，
 * 每条可见性分支都能被测试钉住。</p>
 *
 * <p><b>核心约定：只校验当前可见的字段。</b>页面有两种模式（登录 / 注册）× 两种登录方式
 * （账密 / 验证码），字段组按下面的 `shows*` 判定显示；<b>没显示的字段一律跳过校验</b>，
 * 而不是校验后再忽略错误 —— 后者会让隐藏字段的错误文案残留，下次切换方式时突然冒出来。</p>
 */

/** 登录方式：账密 / 手机验证码。注册态不做切换，恒为账密。 */
export type LoginMethod = 'password' | 'sms'

/** 页面模式：登录 / 注册。 */
export type LoginMode = 'login' | 'register'

export type LoginFieldName = 'username' | 'password' | 'phone' | 'code'

/** 字段级错误：空串表示该字段无错。 */
export type LoginFieldErrors = Record<LoginFieldName, string>

/** 待校验的表单值（只取用得到的四项）。 */
export interface LoginFormValues {
  username: string
  password: string
  phone: string
  code: string
}

/** 决定哪些字段可见的上下文。 */
export interface LoginFormContext {
  method: LoginMethod
  mode: LoginMode
}

// ── 规则常量 ──────────────────────────────────────────────────────────
// 用户名与注册口径完全一致（4~20 位字母数字下划线）：注册时不让填的字符，登录时也没必要放行。
// 密码只对齐长度上限，强度仍只在注册侧要求（否则 demo/123456 这类历史账号登不进来）。

export const USERNAME_RE = /^[a-zA-Z0-9_]+$/
export const USERNAME_MIN = 4
export const USERNAME_MAX = 20
export const PASSWORD_MAX = 32
export const PHONE_RE = /^1[3-9]\d{9}$/
export const SMS_CODE_RE = /^\d{6}$/

/**
 * 账密字段组是否可见。
 *
 * <p>与模板 `v-if="method === 'password' || isRegister"` 严格一致 ——
 * 改模板时这里必须同步改，否则又会出现「校验了看不见的字段」。</p>
 */
export function showsCredentialFields(ctx: LoginFormContext): boolean {
  return ctx.method === 'password' || ctx.mode === 'register'
}

/**
 * 手机号 / 验证码字段组是否可见。
 *
 * <p>与模板 `v-if="method === 'sms' || isRegister"` 严格一致。</p>
 */
export function showsPhoneFields(ctx: LoginFormContext): boolean {
  return ctx.method === 'sms' || ctx.mode === 'register'
}

// ── 单字段校验 ────────────────────────────────────────────────────────

export function validateUsername(form: LoginFormValues, ctx: LoginFormContext): string {
  // 短信登录时用户名输入框不显示，值为空是正常的，不能拦提交
  if (!showsCredentialFields(ctx)) return ''
  const value = form.username.trim()
  if (!value) return '请输入用户名'
  if (value.length < USERNAME_MIN || value.length > USERNAME_MAX) {
    return `用户名长度需在 ${USERNAME_MIN}~${USERNAME_MAX} 位`
  }
  if (!USERNAME_RE.test(value)) return '用户名只能包含字母、数字、下划线'
  return ''
}

export function validatePassword(form: LoginFormValues, ctx: LoginFormContext): string {
  // 同上：短信登录时密码框不显示
  if (!showsCredentialFields(ctx)) return ''
  const value = form.password
  if (!value) return '请输入密码'
  if (value.length > PASSWORD_MAX) return `密码最长 ${PASSWORD_MAX} 位`
  if (/\s/.test(value)) return '密码不能包含空格'
  // 强度只在注册侧要求：登录侧卡复杂度会把历史账号挡在门外
  if (ctx.mode === 'register') {
    if (value.length < 8) return '密码至少 8 位'
    if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) return '密码需同时包含字母和数字'
  }
  return ''
}

export function validatePhone(form: LoginFormValues, ctx: LoginFormContext): string {
  if (!showsPhoneFields(ctx)) return ''
  if (!form.phone) return ctx.method === 'sms' ? '请输入手机号' : ''
  if (!PHONE_RE.test(form.phone)) return '请输入正确的手机号'
  return ''
}

export function validateCode(form: LoginFormValues, ctx: LoginFormContext): string {
  if (!showsPhoneFields(ctx)) return ''
  if (!form.code) return ctx.method === 'sms' ? '请输入验证码' : ''
  if (!SMS_CODE_RE.test(form.code)) return '验证码为 6 位数字'
  return ''
}

const VALIDATORS: Record<LoginFieldName, (form: LoginFormValues, ctx: LoginFormContext) => string> = {
  username: validateUsername,
  password: validatePassword,
  phone: validatePhone,
  code: validateCode,
}

/** 校验单个字段（失焦即时校验用）。 */
export function validateLoginField(
  name: LoginFieldName,
  form: LoginFormValues,
  ctx: LoginFormContext
): string {
  return VALIDATORS[name](form, ctx)
}

/**
 * 校验整张表单，返回每个字段的错误（空串 = 通过）。
 *
 * <p>提交前必须走这里：只要有一项非空就不发请求。因为隐藏字段已被各自短路成空串，
 * 「短信登录被账密校验拦住」不会再发生。</p>
 */
export function validateLoginForm(form: LoginFormValues, ctx: LoginFormContext): LoginFieldErrors {
  return {
    username: validateUsername(form, ctx),
    password: validatePassword(form, ctx),
    phone: validatePhone(form, ctx),
    code: validateCode(form, ctx),
  }
}

/** 是否有任何字段报错。 */
export function hasFieldError(errors: LoginFieldErrors): boolean {
  return Object.values(errors).some(Boolean)
}
