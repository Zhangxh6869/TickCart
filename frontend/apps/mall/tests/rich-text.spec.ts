import { describe, expect, it } from 'vitest'
import { sanitizeRichText } from '../src/utils/rich-text'

/**
 * 商品详情富文本清洗的护栏。
 *
 * <p>这套断言守的是**安全边界**，不是排版细节：只要有一条漏掉，后台一旦被写入
 * 恶意详情，前台就会直接执行。所以每条用例都写成「输入一段攻击载荷 → 断言载荷
 * 已被剥掉、而正常内容还在」，而不是只断言输出等于某个字符串 —— 后者一改文案就红。</p>
 */
describe('sanitizeRichText', () => {
  it('保留图文详情需要的标签与 class', () => {
    const html =
      '<section class="pd-block"><h3 class="pd-h">产品亮点</h3>' +
      '<p class="pd-p">温和配方</p><ul class="pd-list"><li>起泡绵密</li></ul></section>'
    expect(sanitizeRichText(html)).toBe(html)
  })

  it('图片保留 src / alt，并自闭合', () => {
    const out = sanitizeRichText('<img class="pd-img" src="/img/skincare/201-1.jpg" alt="洁面乳">')
    expect(out).toBe('<img class="pd-img" src="/img/skincare/201-1.jpg" alt="洁面乳" />')
  })

  it('丢掉没有 src 的图片，不留破图占位', () => {
    expect(sanitizeRichText('<p>前<img alt="x">后</p>')).toBe('<p>前后</p>')
  })

  it('整段丢弃 script，不把脚本源码印到页面上', () => {
    const out = sanitizeRichText('<p>正文</p><script>alert("xss")</script><p>结尾</p>')
    expect(out).toBe('<p>正文</p><p>结尾</p>')
    expect(out).not.toContain('alert')
  })

  it('丢弃 style / iframe 及其内容', () => {
    const out = sanitizeRichText('<style>body{display:none}</style><iframe src="//evil"></iframe><p>ok</p>')
    expect(out).toBe('<p>ok</p>')
  })

  it('剥掉 on* 事件属性', () => {
    const out = sanitizeRichText('<img src="/img/a.jpg" onerror="alert(1)" onclick="alert(2)">')
    expect(out).toBe('<img src="/img/a.jpg" />')
    expect(out).not.toContain('alert')
  })

  it('拦掉 javascript: 协议的链接', () => {
    const out = sanitizeRichText('<a href="javascript:alert(1)">点我</a>')
    expect(out).toBe('<a>点我</a>')
  })

  it('拦掉 data: 协议的内联图片', () => {
    const out = sanitizeRichText('<img src="data:image/svg+xml;base64,PHN2Zz4=">')
    expect(out).toBe('')
  })

  it('放行站内相对路径与 http(s) 链接', () => {
    expect(sanitizeRichText('<img src="/img/a.jpg">')).toContain('src="/img/a.jpg"')
    expect(sanitizeRichText('<img src="https://cdn.example.com/a.jpg">')).toContain(
      'src="https://cdn.example.com/a.jpg"',
    )
  })

  it('不认识的标签拆壳留字', () => {
    expect(sanitizeRichText('<marquee>滚动文字</marquee>')).toBe('滚动文字')
  })

  it('丢掉注释', () => {
    expect(sanitizeRichText('<p>a</p><!-- 内部备注 --><p>b</p>')).toBe('<p>a</p><p>b</p>')
  })

  it('属性值里的 > 不会把标签切错位置', () => {
    const out = sanitizeRichText('<img alt="a > b" src="/img/a.jpg"><p>后面</p>')
    expect(out).toContain('src="/img/a.jpg"')
    expect(out).toContain('<p>后面</p>')
  })

  it('空值直接返回空串', () => {
    expect(sanitizeRichText(null)).toBe('')
    expect(sanitizeRichText(undefined)).toBe('')
    expect(sanitizeRichText('')).toBe('')
  })
})
