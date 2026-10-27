import { describe, expect, it } from 'vitest'
import { PAY_WINDOW_MS, formatCountdown, remainMsOf } from '@/composables/usePayCountdown'

/**
 * 支付倒计时。
 *
 * <p>收银台、订单详情、订单列表三处共用，最容易错的是两件事：
 * 后端给的时间是 `yyyy-MM-dd HH:mm:ss`（空格分隔，Safari 直接解析会 Invalid Date），
 * 以及过期时不能返回负数 —— 否则页面会显示 `-1:-1`。</p>
 */
const BASE = new Date('2026-10-27T10:00:00').getTime()

describe('PAY_WINDOW_MS', () => {
  it('与后端 timeout-ttl-ms 的默认值一致（15 分钟）', () => {
    expect(PAY_WINDOW_MS).toBe(15 * 60 * 1000)
  })
})

describe('remainMsOf 剩余毫秒', () => {
  it('刚下单时剩余整整一个支付窗口', () => {
    expect(remainMsOf('2026-10-27 10:00:00', BASE)).toBe(PAY_WINDOW_MS)
  })

  it('下单 1 分钟后剩余少 60 秒', () => {
    expect(remainMsOf('2026-10-27 10:00:00', BASE + 60_000)).toBe(PAY_WINDOW_MS - 60_000)
  })

  it('过期返回 0 而不是负数', () => {
    expect(remainMsOf('2026-10-27 10:00:00', BASE + PAY_WINDOW_MS + 1)).toBe(0)
  })

  it('空格分隔的时间能被正确解析（浏览器对 "yyyy-MM-dd HH:mm:ss" 支持不一致）', () => {
    expect(remainMsOf('2026-10-27 10:00:00', BASE)).toBe(remainMsOf('2026-10-27T10:00:00', BASE))
  })

  it('空值与非法时间一律按已过期处理', () => {
    expect(remainMsOf(null, BASE)).toBe(0)
    expect(remainMsOf(undefined, BASE)).toBe(0)
    expect(remainMsOf('', BASE)).toBe(0)
    expect(remainMsOf('不是时间', BASE)).toBe(0)
  })
})

describe('formatCountdown 剩余毫秒转 mm:ss', () => {
  it('零显示 00:00', () => {
    expect(formatCountdown(0)).toBe('00:00')
  })

  it('不足一分钟只占秒位并补零', () => {
    expect(formatCountdown(59_000)).toBe('00:59')
    expect(formatCountdown(5_000)).toBe('00:05')
  })

  it('整分钟进位', () => {
    expect(formatCountdown(60_000)).toBe('01:00')
  })

  it('接近窗口末尾（14:59）', () => {
    expect(formatCountdown(899_999)).toBe('14:59')
  })

  it('负数按 0 处理，不出现 -01:-1 这类文案', () => {
    expect(formatCountdown(-5_000)).toBe('00:00')
  })
})
