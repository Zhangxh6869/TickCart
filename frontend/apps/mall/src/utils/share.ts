/**
 * 分享能力收口。
 *
 * <p>为什么集中在这里：分享链接的口径、各渠道的跳转地址、微信内置浏览器的判定，
 * 都是「多处会用、且必须一致」的约定。散在组件里就会出现「详情页分享出去的链接
 * 和首页卡片分享出去的不是同一个」这类问题 —— 与 {@code utils/checkout-route.ts} 同样的理由。</p>
 *
 * <p><b>平台限制（不是没实现，是 H5 做不到）：</b></p>
 * <ul>
 *   <li>微信 / 朋友圈没有网页分享入口，H5 只能引导用户点右上角「···」；</li>
 *   <li>微信内置浏览器会拦截 QQ / QQ空间 / 微博 的分享页域名（打开即白屏），
 *       必须引导用户改用外部浏览器。</li>
 * </ul>
 */

/** 微信内置浏览器（含企业微信）。 */
export function isWechatBrowser(): boolean {
  return /micromessenger/i.test(navigator.userAgent)
}

/** 商品分享链接。origin 取运行时值，本地 / 线上自动适配。 */
export function buildProductShareUrl(productId: number): string {
  return `${window.location.origin}/products/${productId}`
}

/** 相对地址补成绝对地址 —— 第三方分享页抓不到相对路径的图片。 */
export function toAbsoluteUrl(url: string | null | undefined): string {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  return `${window.location.origin}${url.startsWith('/') ? '' : '/'}${url}`
}

/** 分享面板上的动作标识。与展示名解耦 —— 文案将来会改，动作不能跟着改。 */
export type ShareAction =
  | 'wechat'
  | 'moments'
  | 'qq'
  | 'qzone'
  | 'weibo'
  | 'link'
  | 'poster'
  | 'report'
  | 'feedback'
  | 'more'

/** 有网页分享入口的第三方渠道（微信两兄弟没有，不在此列）。 */
export type ShareChannel = 'qq' | 'qzone' | 'weibo'

/** 引导蒙层的三种文案场景。 */
export type ShareGuideMode = 'wechat' | 'moments' | 'external'

/** 被分享的商品信息。只带分享真正用得到的字段，好让首页卡片 / 秒杀页也能直接复用。 */
export interface ShareProduct {
  id: number
  name: string
  price: number
  mainImage: string | null
}

export interface ShareTarget {
  url: string
  title: string
  /** 商品主图绝对地址，第三方分享页用它做缩略图 */
  image: string
}

const encode = encodeURIComponent

/**
 * 各渠道的官方网页分享入口。
 * 参数一律 encodeURIComponent —— 商品名里带 `&`、`#`、中文都会把 URL 拆坏。
 */
const CHANNEL_URLS: Record<ShareChannel, (target: ShareTarget) => string> = {
  qq: (t) =>
    `https://connect.qq.com/widget/shareqq/index.html?url=${encode(t.url)}&title=${encode(t.title)}&summary=${encode(t.title)}&pics=${encode(t.image)}`,
  qzone: (t) =>
    `https://sns.qzone.qq.com/cgi-bin/qzshare/cgi_qzshare_onekey?url=${encode(t.url)}&title=${encode(t.title)}&summary=${encode(t.title)}&pics=${encode(t.image)}`,
  weibo: (t) =>
    `https://service.weibo.com/share/share.php?url=${encode(t.url)}&title=${encode(t.title)}&pic=${encode(t.image)}`,
}

/** 在外部浏览器里打开渠道分享页。 */
export function openShareChannel(channel: ShareChannel, target: ShareTarget): void {
  window.open(CHANNEL_URLS[channel](target), '_blank', 'noopener,noreferrer')
}

/**
 * 复制文本到剪贴板。
 *
 * <p>{@code navigator.clipboard} 要求安全上下文（https / localhost），生产若是 http 部署
 * 直接不可用，所以必须有 {@code execCommand} 降级 —— 微信 webview 里通常靠这条生效。</p>
 *
 * <p>★ 调用方必须在用户手势里**同步**发起（前面不能有 await），否则会丢手势上下文。</p>
 */
export async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 落到下面的降级分支
    }
  }

  const el = document.createElement('textarea')
  el.value = text
  el.setAttribute('readonly', '')
  el.style.cssText = 'position:fixed;top:-9999px;opacity:0'
  document.body.appendChild(el)
  el.select()
  el.setSelectionRange(0, text.length)
  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }
  document.body.removeChild(el)
  return ok
}
