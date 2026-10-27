import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildProductShareUrl, isWechatBrowser, toAbsoluteUrl } from '@/utils/share'

/**
 * 分享链接口径。
 *
 * <p>三处入口（详情页、卡片、海报）必须给出同一个链接，所以集中在这里。
 * 依赖 `window.location.origin` 与 `navigator.userAgent` —— 用 `vi.stubGlobal` 打桩，
 * 不为两个纯函数引入 jsdom。</p>
 */
const ORIGIN = 'http://localhost:5173'

function stubBrowser(userAgent: string) {
  vi.stubGlobal('window', { location: { origin: ORIGIN } })
  vi.stubGlobal('navigator', { userAgent })
}

describe('分享工具', () => {
  beforeEach(() => {
    stubBrowser('Mozilla/5.0 (Windows NT 10.0) Chrome/155.0')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('isWechatBrowser', () => {
    it('微信内置浏览器（iPhone）', () => {
      stubBrowser(
        'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 MicroMessenger/8.0.49',
      )
      expect(isWechatBrowser()).toBe(true)
    })

    it('企业微信同样识别（UA 里也带 MicroMessenger）', () => {
      stubBrowser('Mozilla/5.0 (Linux; Android 14) wxwork/4.1.20 MicroMessenger/7.0.1')
      expect(isWechatBrowser()).toBe(true)
    })

    it('大小写不敏感', () => {
      stubBrowser('Mozilla/5.0 micromessenger/8.0')
      expect(isWechatBrowser()).toBe(true)
    })

    it('普通浏览器为 false', () => {
      expect(isWechatBrowser()).toBe(false)
    })
  })

  describe('buildProductShareUrl', () => {
    it('用运行时 origin 拼商品详情地址（本地与线上自动适配）', () => {
      expect(buildProductShareUrl(302)).toBe(`${ORIGIN}/products/302`)
    })
  })

  describe('toAbsoluteUrl', () => {
    it('已经是绝对地址就原样返回', () => {
      expect(toAbsoluteUrl('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png')
      expect(toAbsoluteUrl('http://cdn.example.com/a.png')).toBe('http://cdn.example.com/a.png')
    })

    it('以 / 开头的相对地址只补 origin，不重复斜杠', () => {
      expect(toAbsoluteUrl('/img/a.png')).toBe(`${ORIGIN}/img/a.png`)
    })

    it('不以 / 开头的相对地址补一个斜杠', () => {
      expect(toAbsoluteUrl('img/a.png')).toBe(`${ORIGIN}/img/a.png`)
    })

    it('空值返回空串（第三方分享页拿不到图时不至于渲染成 "null"）', () => {
      expect(toAbsoluteUrl(null)).toBe('')
      expect(toAbsoluteUrl(undefined)).toBe('')
      expect(toAbsoluteUrl('')).toBe('')
    })

    it('协议大小写不敏感', () => {
      expect(toAbsoluteUrl('HTTPS://cdn.example.com/a.png')).toBe('HTTPS://cdn.example.com/a.png')
    })
  })
})
