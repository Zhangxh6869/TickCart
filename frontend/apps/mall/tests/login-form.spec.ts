import { describe, it, expect } from 'vitest'
import {
  validateLoginForm,
  validateLoginField,
  hasFieldError,
  showsUsername,
  showsPhone,
  showsPassword,
  showsCode,
  type LoginFormContext,
  type LoginFormValues,
} from '../src/utils/login-form'

/** 造一份表单值，只覆盖需要的字段，避免每条用例都写全。 */
function form(patch: Partial<LoginFormValues> = {}): LoginFormValues {
  return { username: '', password: '', phone: '', code: '', ...patch }
}

const SMS_LOGIN: LoginFormContext = { method: 'sms', mode: 'login' }
const PWD_LOGIN: LoginFormContext = { method: 'password', mode: 'login' }
const REGISTER: LoginFormContext = { method: 'password', mode: 'register' }

describe('字段可见性', () => {
  it('账密登录：手机号 + 密码，用户名与验证码不显示', () => {
    expect(showsUsername(PWD_LOGIN)).toBe(false)
    expect(showsPhone(PWD_LOGIN)).toBe(true)
    expect(showsPassword(PWD_LOGIN)).toBe(true)
    expect(showsCode(PWD_LOGIN)).toBe(false)
  })

  it('验证码登录：手机号 + 验证码，用户名与密码不显示', () => {
    expect(showsUsername(SMS_LOGIN)).toBe(false)
    expect(showsPhone(SMS_LOGIN)).toBe(true)
    expect(showsPassword(SMS_LOGIN)).toBe(false)
    expect(showsCode(SMS_LOGIN)).toBe(true)
  })

  it('注册：四项全显示', () => {
    expect(showsUsername(REGISTER)).toBe(true)
    expect(showsPhone(REGISTER)).toBe(true)
    expect(showsPassword(REGISTER)).toBe(true)
    expect(showsCode(REGISTER)).toBe(true)
  })

  it('手机号在两种登录方式下都显示（切换方式不该让它消失）', () => {
    expect(showsPhone(PWD_LOGIN)).toBe(true)
    expect(showsPhone(SMS_LOGIN)).toBe(true)
  })
})

describe('账密登录（账号为手机号）', () => {
  it('手机号与密码都必填', () => {
    const errors = validateLoginForm(form(), PWD_LOGIN)
    expect(errors.phone).toBe('请输入手机号')
    expect(errors.password).toBe('请输入密码')
  })

  it('用户名不参与登录校验（输入框根本没渲染）', () => {
    const errors = validateLoginForm(form({ phone: '13900000000', password: '123456' }), PWD_LOGIN)
    expect(errors.username).toBe('')
    expect(hasFieldError(errors)).toBe(false)
  })

  it('登录侧不卡密码强度（历史账号 13900000000/123456 必须能进）', () => {
    const errors = validateLoginForm(form({ phone: '13900000000', password: '123456' }), PWD_LOGIN)
    expect(errors.password).toBe('')
  })

  it('手机号格式不对要报错', () => {
    expect(validateLoginForm(form({ phone: '12345678901', password: 'x' }), PWD_LOGIN).phone).toBe(
      '请输入正确的手机号'
    )
  })

  it('密码不能含空格、不能超长', () => {
    const ok = (password: string) =>
      validateLoginForm(form({ phone: '13900000000', password }), PWD_LOGIN).password
    expect(ok('a b')).toBe('密码不能包含空格')
    expect(ok('a'.repeat(33))).toBe('密码最长 32 位')
  })
})

describe('验证码登录（回归：曾因校验隐藏字段导致点登录无反应）', () => {
  it('手机号与验证码都必填', () => {
    const errors = validateLoginForm(form(), SMS_LOGIN)
    expect(errors.phone).toBe('请输入手机号')
    expect(errors.code).toBe('请输入验证码')
  })

  it('密码不参与校验 —— 这个输入框没渲染，值为空是正常的', () => {
    const errors = validateLoginForm(form({ phone: '13700000000', code: '123456' }), SMS_LOGIN)
    expect(errors.password).toBe('')
    expect(hasFieldError(errors)).toBe(false)
  })

  it('单字段校验同样跳过密码', () => {
    const values = form({ phone: '13700000000', code: '123456' })
    expect(validateLoginField('password', values, SMS_LOGIN)).toBe('')
  })

  it('验证码必须是 6 位数字', () => {
    const base = { phone: '13700000000' }
    expect(validateLoginForm(form({ ...base, code: '12345' }), SMS_LOGIN).code).toBe('验证码为 6 位数字')
    expect(validateLoginForm(form({ ...base, code: '1234567' }), SMS_LOGIN).code).toBe('验证码为 6 位数字')
    expect(validateLoginForm(form({ ...base, code: '12345a' }), SMS_LOGIN).code).toBe('验证码为 6 位数字')
    expect(validateLoginForm(form({ ...base, code: '123456' }), SMS_LOGIN).code).toBe('')
  })

  it('手机号格式不对要报错', () => {
    expect(validateLoginForm(form({ phone: '12345678901', code: '123456' }), SMS_LOGIN).phone).toBe(
      '请输入正确的手机号'
    )
  })
})

describe('注册（四项必填）', () => {
  it('四项都为空时四项都报错', () => {
    const errors = validateLoginForm(form(), REGISTER)
    expect(errors.username).toBe('请输入用户名')
    expect(errors.phone).toBe('请输入手机号')
    expect(errors.password).toBe('请输入密码')
    expect(errors.code).toBe('请输入验证码')
  })

  it('四项填全后通过', () => {
    const errors = validateLoginForm(
      form({
        username: 'newuser',
        phone: '13700000000',
        password: 'abc12345',
        code: '483920',
      }),
      REGISTER
    )
    expect(hasFieldError(errors)).toBe(false)
  })

  it('注册侧要求密码强度（登录侧不要求）', () => {
    expect(
      validateLoginForm(form({ username: 'newuser', phone: '13700000000', password: '12345678' }), REGISTER)
        .password
    ).toBe('密码需同时包含字母和数字')
    expect(
      validateLoginForm(form({ username: 'newuser', phone: '13700000000', password: 'abc123' }), REGISTER)
        .password
    ).toBe('密码至少 8 位')
  })

  it('用户名长度与字符集', () => {
    const ok = (username: string) =>
      validateLoginForm(
        form({ username, phone: '13700000000', password: 'abc12345', code: '483920' }),
        REGISTER
      ).username
    expect(ok('abc')).toBe('用户名长度需在 4~20 位')
    expect(ok('a'.repeat(21))).toBe('用户名长度需在 4~20 位')
    expect(ok('demo user')).toBe('用户名只能包含字母、数字、下划线')
    expect(ok('demo_user')).toBe('')
  })

  it('手机号格式不对要报错', () => {
    const errors = validateLoginForm(
      form({ username: 'newuser', phone: '110', password: 'abc12345', code: '483920' }),
      REGISTER
    )
    expect(errors.phone).toBe('请输入正确的手机号')
  })
})

describe('hasFieldError', () => {
  it('全空串才算通过', () => {
    expect(hasFieldError({ username: '', password: '', phone: '', code: '' })).toBe(false)
    expect(hasFieldError({ username: '', password: '', phone: 'x', code: '' })).toBe(true)
  })
})
