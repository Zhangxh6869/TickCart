import { describe, expect, it } from 'vitest'
import { formatCount, formatCountdown, formatPrice, maskPhone } from '@tickcart/shared-utils'

/**
 * 共享工具函数：两个 app 都在用，写错一处就两边一起错，所以边界要穷举。
 */
describe('formatPrice 分转元', () => {
  it('整数补两位小数', () => {
    expect(formatPrice(12)).toBe('12.00')
    expect(formatPrice(0)).toBe('0.00')
  })

  it('字符串数字也按数值处理（后端金额常是字符串）', () => {
    expect(formatPrice('9.9')).toBe('9.90')
    expect(formatPrice('39.90')).toBe('39.90')
  })

  it('空值统一显示 0.00，不显示 NaN 或 undefined', () => {
    expect(formatPrice(null)).toBe('0.00')
    expect(formatPrice(undefined)).toBe('0.00')
    expect(formatPrice('')).toBe('0.00')
  })

  it('非数字字符串回落 0.00', () => {
    expect(formatPrice('abc')).toBe('0.00')
  })

  it('负数保留符号', () => {
    expect(formatPrice(-1)).toBe('-1.00')
  })

  it('四舍五入到分', () => {
    expect(formatPrice(0.004)).toBe('0.00')
    expect(formatPrice(0.006)).toBe('0.01')
  })
})

describe('formatCount 大数字缩写', () => {
  it('0 与空值都显示 0', () => {
    expect(formatCount(0)).toBe('0')
    expect(formatCount(null)).toBe('0')
    expect(formatCount(undefined)).toBe('0')
  })

  it('一万以下原样显示', () => {
    expect(formatCount(9999)).toBe('9999')
  })

  it('一万整变成 1.00万（边界值最容易写错）', () => {
    expect(formatCount(10000)).toBe('1.00万')
  })

  it('一万以上保留两位小数', () => {
    expect(formatCount(12800)).toBe('1.28万')
    expect(formatCount(123456)).toBe('12.35万')
  })
})

describe('maskPhone 手机号脱敏', () => {
  it('正常 11 位：保留前 3 后 4', () => {
    expect(maskPhone('13800001234')).toBe('138****1234')
  })

  it('不足 7 位不脱敏，原样返回（脱敏会把号段全遮住）', () => {
    expect(maskPhone('123456')).toBe('123456')
  })

  it('空值返回空串而不是 undefined', () => {
    expect(maskPhone(null)).toBe('')
    expect(maskPhone(undefined)).toBe('')
  })
})

describe('formatCountdown 秒级倒计时', () => {
  it('已过期显示 00:00:00，不出现负数', () => {
    expect(formatCountdown(1000, 5000)).toBe('00:00:00')
  })

  it('跨小时正确进位', () => {
    // 3661 秒 = 01:01:01
    expect(formatCountdown(3_661_000, 0)).toBe('01:01:01')
  })

  it('不足一分钟只占秒位', () => {
    expect(formatCountdown(59_000, 0)).toBe('00:00:59')
  })

  it('各段补零到两位', () => {
    expect(formatCountdown(65_000, 0)).toBe('00:01:05')
  })
})
