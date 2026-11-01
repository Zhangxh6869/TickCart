/**
 * 登录 / 注册表单校验（纯函数，不依赖 Vue 与 DOM）。
 *
 * <p><b>为什么单独抽出来</b>：这套规则原本写在 `LoginView.vue` 的 `&lt;script setup&gt;` 里，
 * 单测碰不到组件内部，于是「短信登录时账密字段被隐藏、却仍被校验」这个回归一路溜到了线上
 * （点登录毫无反应，见 `tests/login-form.spec.ts` 的回归用例）。规则挪到纯函数后，
 * 每条可见性分支都能被测试钉住。</p>
 *
 * <p><b>核心约定：只校验当前可见的字段。</b>页面有两种模式（登录 / 注册）× 两种登录方式
 * （账密 / 验证码），字段可见性见下方 `shows*`；<b>没显示的字段一律跳过校验</b>，
 * 而不是校验后再忽略错误 —— 后者会让隐藏字段的错误文案残留，下次切换方式时突然冒出来。</p>
 *
 * <p><b>为什么按「单个字段」判定可见性</b>：改造后登录账号统一为手机号，手机号在两种登录方式
 * 下都要显示，只有第二项（密码 / 验证码）随方式切换。原来的「账密组 / 手机组」二分法
 * 表达不了这个结构（手机号既属于手机组、又要常驻），因此改为逐字段判定。</p>
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
// 密码只对齐长度上限，强度仍只在注册侧要求（否则 demo 这类历史账号登不进来）。

export const USERNAME_RE = /^[a-zA-Z0-9_]+$/
export const USERNAME_MIN = 4
export const USERNAME_MAX = 20
export const PASSWORD_MAX = 32
export const PHONE_RE = /^1[3-9]\d{9}$/
export const SMS_CODE_RE = /^\d{6}$/

/**
 * 用户名是否可见：**仅注册**。
 *
 * <p>登录不再接受用户名，所以登录态这个输入框根本不渲染，值为空是正常的。</p>
 */
export function showsUsername(ctx: LoginFormContext): boolean {
  return ctx.mode === 'register'
}

/**
 * 手机号是否可见：**恒可见**。
 *
 * <p>手机号是唯一登录账号，账密登录与验证码登录共用它，切换方式时不清空。</p>
 */
export function showsPhone(_ctx: LoginFormContext): boolean {
  return true
}

/** 密码是否可见：账密登录 或 注册。 */
export function showsPassword(ctx: LoginFormContext): boolean {
  return ctx.mode === 'register' || ctx.method === 'password'
}

/** 验证码是否可见：验证码登录 或 注册。 */
export function showsCode(ctx: LoginFormContext): boolean {
  return ctx.mode === 'register' || ctx.method === 'sms'
}

// ── 单字段校验 ────────────────────────────────────────────────────────

export function validateUsername(form: LoginFormValues, ctx: LoginFormContext): string {
  if (!showsUsername(ctx)) return ''
  const value = form.username.trim()
  if (!value) return '请输入用户名'
  if (value.length < USERNAME_MIN || value.length > USERNAME_MAX) {
    return `用户名长度需在 ${USERNAME_MIN}~${USERNAME_MAX} 位`
  }
  if (!USERNAME_RE.test(value)) return '用户名只能包含字母、数字、下划线'
  return ''
}

export function validatePassword(form: LoginFormValues, ctx: LoginFormContext): string {
  if (!showsPassword(ctx)) return ''
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
  if (!showsPhone(ctx)) return ''
  // 手机号是唯一登录账号，登录与注册都必填
  if (!form.phone) return '请输入手机号'
  if (!PHONE_RE.test(form.phone)) return '请输入正确的手机号'
  return ''
}

export function validateCode(form: LoginFormValues, ctx: LoginFormContext): string {
  if (!showsCode(ctx)) return ''
  if (!form.code) return '请输入验证码'
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
