import { describe, it, expect } from 'vitest'
import {
  validateLoginForm,
  validateLoginField,
  hasFieldError,
  showsCredentialFields,
  showsPhoneFields,
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
  it('短信登录：只显示手机号与验证码', () => {
    expect(showsCredentialFields(SMS_LOGIN)).toBe(false)
    expect(showsPhoneFields(SMS_LOGIN)).toBe(true)
  })

  it('账密登录：只显示账密', () => {
    expect(showsCredentialFields(PWD_LOGIN)).toBe(true)
    expect(showsPhoneFields(PWD_LOGIN)).toBe(false)
  })

  it('注册：两组都显示（一次收齐全部字段）', () => {
    expect(showsCredentialFields(REGISTER)).toBe(true)
    expect(showsPhoneFields(REGISTER)).toBe(true)
  })
})

describe('短信验证码登录（回归：曾因校验隐藏字段导致点登录无反应）', () => {
  it('账密为空不算错 —— 这两个输入框根本没渲染', () => {
    const errors = validateLoginForm(form({ phone: '13700000000', code: '123456' }), SMS_LOGIN)
    expect(errors.username).toBe('')
    expect(errors.password).toBe('')
    expect(hasFieldError(errors)).toBe(false)
  })

  it('单字段校验同样跳过账密', () => {
    const values = form({ phone: '13700000000', code: '123456' })
    expect(validateLoginField('username', values, SMS_LOGIN)).toBe('')
    expect(validateLoginField('password', values, SMS_LOGIN)).toBe('')
  })

  it('手机号缺失时拦住，并给出手机号提示', () => {
    const errors = validateLoginForm(form({ code: '123456' }), SMS_LOGIN)
    expect(errors.phone).toBe('请输入手机号')
    expect(errors.username).toBe('')
  })

  it('验证码缺失时拦住', () => {
    const errors = validateLoginForm(form({ phone: '13700000000' }), SMS_LOGIN)
    expect(errors.code).toBe('请输入验证码')
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

describe('账密登录', () => {
  it('账密都为空时两项都报错', () => {
    const errors = validateLoginForm(form(), PWD_LOGIN)
    expect(errors.username).toBe('请输入用户名')
    expect(errors.password).toBe('请输入密码')
  })

  it('登录侧不卡密码强度（历史账号 demo/123456 必须能进）', () => {
    const errors = validateLoginForm(form({ username: 'demo', password: '123456' }), PWD_LOGIN)
    expect(hasFieldError(errors)).toBe(false)
  })

  it('手机号与验证码不参与账密登录的校验', () => {
    const errors = validateLoginForm(form({ username: 'demo', password: '123456' }), PWD_LOGIN)
    expect(errors.phone).toBe('')
    expect(errors.code).toBe('')
  })

  it('用户名长度与字符集', () => {
    const ok = (username: string) => validateLoginForm(form({ username, password: 'x' }), PWD_LOGIN).username
    expect(ok('abc')).toBe('用户名长度需在 4~20 位')
    expect(ok('a'.repeat(21))).toBe('用户名长度需在 4~20 位')
    expect(ok('demo user')).toBe('用户名只能包含字母、数字、下划线')
    expect(ok('demo_user')).toBe('')
  })

  it('密码不能含空格、不能超长', () => {
    const ok = (password: string) => validateLoginForm(form({ username: 'demo', password }), PWD_LOGIN).password
    expect(ok('a b')).toBe('密码不能包含空格')
    expect(ok('a'.repeat(33))).toBe('密码最长 32 位')
  })
})

describe('注册', () => {
  it('四项都要校验', () => {
    const errors = validateLoginForm(form(), REGISTER)
    expect(errors.username).toBe('请输入用户名')
    expect(errors.password).toBe('请输入密码')
    expect(errors.phone).toBe('')
    expect(errors.code).toBe('')
  })

  it('注册侧要求密码强度', () => {
    const errors = validateLoginForm(form({ username: 'newuser', password: '12345678' }), REGISTER)
    expect(errors.password).toBe('密码需同时包含字母和数字')
  })

  it('注册时手机号与验证码同样必填', () => {
    const errors = validateLoginForm(
      form({ username: 'newuser', password: 'abc12345' }),
      REGISTER
    )
    // 注册态没填手机号：提示文案是空串（手机号在注册里是可选的，由后端判定），
    // 但只要填了就必须合法
    expect(errors.phone).toBe('')
    const bad = validateLoginForm(
      form({ username: 'newuser', password: 'abc12345', phone: '110' }),
      REGISTER
    )
    expect(bad.phone).toBe('请输入正确的手机号')
  })
})

describe('hasFieldError', () => {
  it('全空串才算通过', () => {
    expect(hasFieldError({ username: '', password: '', phone: '', code: '' })).toBe(false)
    expect(hasFieldError({ username: '', password: '', phone: 'x', code: '' })).toBe(true)
  })
})
