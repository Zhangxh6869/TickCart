/**
 * 商品详情的富文本清洗。
 *
 * <p>详情文案（{@code product.detail}）现在存的是 HTML —— 图文详情要有标题、列表、
 * 参数表和插图，纯文本表达不了。渲染它就必须过一遍白名单：这份内容虽然只由后台管理员
 * 写入，但「后台能写什么，前台就渲染什么」等于把 XSS 的门直接敞开，一旦后台账号被盗或
 * 有人拿到接口权限，前台就是执行点。</p>
 *
 * <p><b>为什么不用 DOMPurify</b>：本项目前端依赖由开发者统一管理（沙箱里不能跑
 * {@code pnpm install}），为一次清洗引入一个运行时依赖不划算；而清洗规则本身很短，
 * 白名单固定，自己写反而更好审计。另一个好处是**不依赖 DOM** —— 单测环境是 node
 * （见 {@code vitest.config.ts}），用 {@code DOMParser} 就得为它装一整套 jsdom。</p>
 *
 * <p><b>做法</b>：逐字符扫描，遇到标签就按白名单重建，遇到文本原样保留。
 * 不用「正则整体替换」是因为正则处理不了属性值里带 {@code >} 的情况
 * （{@code <img alt="a > b">}），会切错位置把后面的内容当标签。</p>
 */

/** 允许保留的标签。够用即可，多一个标签就多一分被滥用的可能。 */
const ALLOWED_TAGS = new Set([
  'p',
  'div',
  'span',
  'br',
  'hr',
  'section',
  'figure',
  'figcaption',
  'h3',
  'h4',
  'strong',
  'b',
  'em',
  'i',
  'u',
  'ul',
  'ol',
  'li',
  'table',
  'thead',
  'tbody',
  'tr',
  'th',
  'td',
  'img',
  'a',
])

/**
 * 连同内容一起丢弃的标签。
 *
 * <p>注意与「不认识就拆壳留字」的区别：{@code <script>} 的内容是代码不是文案，
 * 拆壳会把 JS 源码直接印到页面上；{@code <style>} 同理。所以这几个必须整段丢。</p>
 */
const DROP_TAGS = new Set([
  'script',
  'style',
  'iframe',
  'object',
  'embed',
  'link',
  'meta',
  'base',
  'form',
  'input',
  'button',
  'textarea',
  'select',
  'option',
  'svg',
  'math',
  'template',
  'noscript',
])

/** 每个标签允许保留的属性；`*` 是全局兜底。未列出的属性一律丢弃。 */
const ALLOWED_ATTRS: Record<string, Set<string>> = {
  '*': new Set(['class']),
  img: new Set(['src', 'alt', 'width', 'height']),
  a: new Set(['href', 'title']),
  td: new Set(['colspan', 'rowspan']),
  th: new Set(['colspan', 'rowspan']),
}

/** 允许出现在 src / href 里的协议或路径前缀 */
const SAFE_URL = /^(https?:\/\/|\/|\.{1,2}\/|#)/i

const ATTR_RE = /([a-zA-Z_:][\w:.-]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))/g

/** 从 {@code start}（指向 `<`）开始找到标签结束的 `>`，跳过引号内的内容 */
function findTagEnd(html: string, start: number): number {
  let quote = ''
  for (let i = start + 1; i < html.length; i += 1) {
    const ch = html[i]
    if (quote) {
      if (ch === quote) quote = ''
    } else if (ch === '"' || ch === "'") {
      quote = ch
    } else if (ch === '>') {
      return i
    }
  }
  return -1
}

/** 转义属性值里的双引号与尖括号，保证重建出来的标签不会被截断 */
function escapeAttr(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** 按白名单筛出一组属性，返回 ` key="value"` 拼接串 */
function filterAttrs(tag: string, attrSource: string): string {
  const attrs: string[] = []
  const allowed = ALLOWED_ATTRS[tag]
  ATTR_RE.lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = ATTR_RE.exec(attrSource)) !== null) {
    const name = match[1].toLowerCase()
    const value = match[2] ?? match[3] ?? match[4] ?? ''
    // on* 事件属性、以及不在白名单里的属性，一律丢弃
    if (name.startsWith('on')) continue
    if (!allowed?.has(name) && !ALLOWED_ATTRS['*'].has(name)) continue
    if ((name === 'src' || name === 'href') && !SAFE_URL.test(value.trim())) continue
    attrs.push(` ${name}="${escapeAttr(value)}"`)
  }
  return attrs.join('')
}

/** 按白名单重建一个开标签；返回空串表示整个标签被丢弃 */
function rebuildOpenTag(tag: string, attrSource: string): string {
  const attrs = filterAttrs(tag, attrSource)
  // 图片没有可用的 src 就整个丢掉：留个空 <img> 只会渲染成破图占位
  if (tag === 'img' && !attrs.includes(' src=')) return ''
  return `<${tag}${attrs}>`
}

/** 找到与 {@code <tag>} 配对的闭合标签位置（返回闭合标签之后的下标）；找不到就返回 -1 */
function skipToClosingTag(html: string, from: number, tag: string): number {
  const open = new RegExp(`<${tag}(\\s[^>]*)?>`, 'gi')
  const close = new RegExp(`</${tag}\\s*>`, 'gi')
  let depth = 1
  let cursor = from
  while (cursor < html.length) {
    open.lastIndex = cursor
    close.lastIndex = cursor
    const nextOpen = open.exec(html)
    const nextClose = close.exec(html)
    if (!nextClose) return -1
    if (nextOpen && nextOpen.index < nextClose.index) {
      depth += 1
      cursor = nextOpen.index + nextOpen[0].length
      continue
    }
    depth -= 1
    cursor = nextClose.index + nextClose[0].length
    if (depth === 0) return cursor
  }
  return -1
}

/**
 * 清洗商品详情 HTML：保留白名单内的标签与属性，其余一律去掉。
 *
 * <p>返回值可以直接交给 {@code v-html}。</p>
 */
export function sanitizeRichText(html: string | null | undefined): string {
  if (!html) return ''
  const source = String(html)
  const out: string[] = []
  let cursor = 0

  while (cursor < source.length) {
    const lt = source.indexOf('<', cursor)
    if (lt < 0) {
      out.push(source.slice(cursor))
      break
    }
    out.push(source.slice(cursor, lt))

    // 注释整段丢弃
    if (source.startsWith('<!--', lt)) {
      const end = source.indexOf('-->', lt + 4)
      cursor = end < 0 ? source.length : end + 3
      continue
    }

    const gt = findTagEnd(source, lt)
    if (gt < 0) {
      // 没有闭合的 `<` 说明是正文里的裸字符，原样保留
      out.push(source.slice(lt))
      break
    }

    const raw = source.slice(lt, gt + 1)
    const parsed = /^<\s*(\/?)\s*([a-zA-Z][\w:-]*)([\s\S]*?)\/?\s*>$/.exec(raw)
    if (!parsed) {
      cursor = gt + 1
      continue
    }

    const closing = parsed[1] === '/'
    const tag = parsed[2].toLowerCase()
    const rest = parsed[3] ?? ''

    if (DROP_TAGS.has(tag)) {
      if (closing) {
        cursor = gt + 1
        continue
      }
      const after = skipToClosingTag(source, gt + 1, tag)
      // 自闭合或找不到闭合标签时只丢这一个标签本身
      cursor = after < 0 ? gt + 1 : after
      continue
    }

    if (!ALLOWED_TAGS.has(tag)) {
      // 不认识的标签：拆壳留字（内容由后续循环按普通文本处理）
      cursor = gt + 1
      continue
    }

    if (closing) {
      out.push(`</${tag}>`)
    } else if (tag === 'img' || tag === 'br' || tag === 'hr') {
      // 空元素必须自闭合，否则浏览器会把它当成后面所有内容的父节点
      const empty = rebuildOpenTag(tag, rest)
      if (empty) out.push(empty.replace(/>$/, ' />'))
    } else {
      const openTag = rebuildOpenTag(tag, rest)
      if (openTag) out.push(openTag)
    }
    cursor = gt + 1
  }

  return out.join('').trim()
}
