/**
 * 商品详情的「区块模型」：后台可视化编辑与 `product.detail` 里的 HTML 之间的双向转换。
 *
 * <p><b>为什么要有这一层</b>：详情存的是 HTML（商城端用 {@code v-html} 渲染），
 * 但让运营在后台写 HTML 门槛太高。于是把详情拆成「标题 / 段落 / 要点 / 参数表 / 图片 / 分割线」
 * 几种区块，后台按区块编辑，保存时再拼回 HTML。</p>
 *
 * <p><b>往返（HTML → 区块 → HTML）必须稳</b>：解析不出来的内容**绝不能丢**，
 * 一律原样塞进 {@code html} 区块（后台里仍可编辑源码）。所以解析器对每段内容都是
 * 「要么完全认得、要么整段保留」，不做「认得一半就丢掉一半」的猜测。</p>
 *
 * <p><b>产物必须落在商城端的白名单内</b>：{@code mall/src/utils/rich-text.ts} 只放行
 * h3/h4/p/ul/ol/li/table/tr/th/td/img/hr/section 等标签，这里生成的 HTML 只用到这些。</p>
 */

/** 参数表的一行 */
export interface DetailParamRow {
  label: string
  value: string
}

export type DetailBlockType =
  | 'heading'
  | 'paragraph'
  | 'list'
  | 'params'
  | 'image'
  | 'divider'
  | 'html'

interface BlockBase {
  /** 前端渲染用的稳定 key，不参与序列化 */
  id: string
}

/**
 * 一个详情区块。
 *
 * <p>用可辨识联合而不是「一堆可选字段」：区块类型决定了哪些字段有意义，
 * 写成可选字段就等于允许「一个没有 items 的列表块」这种非法状态存在。</p>
 */
export type DetailBlock =
  | (BlockBase & { type: 'heading'; text: string })
  | (BlockBase & { type: 'paragraph'; text: string })
  | (BlockBase & { type: 'list'; items: string[] })
  | (BlockBase & { type: 'params'; rows: DetailParamRow[] })
  | (BlockBase & { type: 'image'; url: string; alt: string })
  | (BlockBase & { type: 'divider' })
  /** 解析器不认识的结构：原样保留 HTML 源码，保证不丢内容 */
  | (BlockBase & { type: 'html'; text: string })

/** 新增区块时可选的类型（html 只能由解析兜底产生，不让用户主动新建） */
export const ADDABLE_BLOCK_TYPES: { type: DetailBlockType; label: string }[] = [
  { type: 'heading', label: '小标题' },
  { type: 'paragraph', label: '段落' },
  { type: 'list', label: '要点列表' },
  { type: 'params', label: '参数表' },
  { type: 'image', label: '图片' },
  { type: 'divider', label: '分割线' },
]

const VOID_TAGS = new Set(['img', 'hr', 'br', 'input', 'meta', 'link'])

/** 这些标签在任意层级都能直接映射成区块 */
const SIMPLE_TAGS: Record<string, DetailBlockType> = {
  h3: 'heading',
  h4: 'heading',
  p: 'paragraph',
  ul: 'list',
  ol: 'list',
  table: 'params',
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function escapeAttr(text: string): string {
  return escapeHtml(text).replace(/"/g, '&quot;')
}

function unescapeHtml(text: string): string {
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, ' ')
    // `&amp;` 必须最后替换，否则 `&amp;lt;` 会被二次解码成 `<`
    .replace(/&amp;/g, '&')
}

/** 文本 → HTML 片段：换行转 `<br>`，其余转义 */
function inlineText(text: string): string {
  return escapeHtml(text).replace(/\n/g, '<br>')
}

/** 取开标签上的属性值 */
function attr(openTag: string, name: string): string {
  const match = new RegExp(`\\s${name}\\s*=\\s*("([^"]*)"|'([^']*)'|([^\\s>]+))`, 'i').exec(openTag)
  if (!match) return ''
  return unescapeHtml(match[2] ?? match[3] ?? match[4] ?? '')
}

/**
 * 读取 {@code start} 处开始的一个元素，返回其内部 HTML 与结束下标。
 *
 * <p>按同名标签配对（计深度），所以嵌套 {@code <div><div>…</div></div>} 也能正确切分。</p>
 */
function readElement(html: string, start: number, tag: string): { inner: string; end: number } | null {
  const openTag = new RegExp(`^<${tag}(\\s[^>]*)?>`, 'i').exec(html.slice(start))
  if (!openTag) return null
  const innerStart = start + openTag[0].length

  const openRe = new RegExp(`<${tag}(\\s[^>]*)?>`, 'gi')
  const closeRe = new RegExp(`</${tag}\\s*>`, 'gi')
  let depth = 1
  let cursor = innerStart

  while (cursor < html.length) {
    openRe.lastIndex = cursor
    closeRe.lastIndex = cursor
    const nextOpen = openRe.exec(html)
    const nextClose = closeRe.exec(html)
    if (!nextClose) return null
    if (nextOpen && nextOpen.index < nextClose.index) {
      depth += 1
      cursor = nextOpen.index + nextOpen[0].length
      continue
    }
    depth -= 1
    if (depth === 0) {
      return { inner: html.slice(innerStart, nextClose.index), end: nextClose.index + nextClose[0].length }
    }
    cursor = nextClose.index + nextClose[0].length
  }
  return null
}

/**
 * 取纯文本；内部若含 `<br>` 以外的标签则返回 {@code null}（表示「认不出来」）。
 *
 * <p>只认 `<br>` 是刻意的：段落里一旦有 `<strong>`/`<a>` 这类行内标签，
 * 存成纯文本就会丢格式，宁可整段退化成 {@code html} 区块保留原样。</p>
 */
function textOf(innerHtml: string): string | null {
  const plain = innerHtml.replace(/<br\s*\/?>/gi, '\n')
  if (plain.includes('<')) return null
  return unescapeHtml(plain).trim()
}

/** `<ul><li>a</li>…</ul>` → 条目数组；认不出返回 null */
function parseListItems(innerHtml: string): string[] | null {
  const items: string[] = []
  const re = /<li(\s[^>]*)?>([\s\S]*?)<\/li>/gi
  let match: RegExpExecArray | null
  while ((match = re.exec(innerHtml)) !== null) {
    const text = textOf(match[2])
    if (text === null) return null
    items.push(text)
  }
  return items.length ? items : null
}

/** `<table><tr><th>k</th><td>v</td></tr></table>` → 键值对；认不出返回 null */
function parseTableRows(innerHtml: string): DetailParamRow[] | null {
  const rows: DetailParamRow[] = []
  const re = /<tr(\s[^>]*)?>([\s\S]*?)<\/tr>/gi
  let match: RegExpExecArray | null
  while ((match = re.exec(innerHtml)) !== null) {
    const rowHtml = match[2]
    const th = /<th(\s[^>]*)?>([\s\S]*?)<\/th>/i.exec(rowHtml)
    const td = /<td(\s[^>]*)?>([\s\S]*?)<\/td>/i.exec(rowHtml)
    if (!th && !td) return null
    const label = th ? textOf(th[2]) : ''
    const value = td ? textOf(td[2]) : ''
    if (label === null || value === null) return null
    rows.push({ label: label ?? '', value: value ?? '' })
  }
  return rows.length ? rows : null
}

/** 把一个已经切出来的元素转成区块；认不出就返回 null（由调用方兜底成 html 区块） */
function toBlock(
  tag: string,
  inner: string,
  raw: string,
  openTag: string,
  id: string,
): DetailBlock | null {
  if (tag === 'img') {
    const url = attr(openTag, 'src')
    return url ? { id, type: 'image', url, alt: attr(openTag, 'alt') } : null
  }
  if (tag === 'hr') return { id, type: 'divider' }

  const simple = SIMPLE_TAGS[tag]
  if (simple === 'heading' || simple === 'paragraph') {
    const text = textOf(inner)
    return text === null ? null : { id, type: simple, text }
  }
  if (simple === 'list') {
    const items = parseListItems(inner)
    return items ? { id, type: 'list', items } : null
  }
  if (simple === 'params') {
    const rows = parseTableRows(inner)
    return rows ? { id, type: 'params', rows } : null
  }
  if (tag === 'section' || tag === 'div') {
    // 带属性的容器不在这里处理（会先被 isTransparentWrapper 拦下当 html 区块保留）
    return null
  }
  // 其它标签：原样保留
  return { id, type: 'html', text: raw }
}

/** 容器标签：往里递归，容器本身不保留 */
const CONTAINER_TAGS = new Set(['section', 'div'])

/**
 * 判断容器是不是「纯排版包裹」，可以拆掉只保留里面的内容。
 *
 * <p>只有两种算：<b>没有任何属性</b>的 section/div，以及我们自己生成的
 * {@code <section class="pd-block">}。其余带 class/style 的容器一律当 html 区块整段保留 ——
 * 拆掉它们会丢掉自定义样式，等于「保存一次就把运营的排版吃掉」。</p>
 */
function isTransparentWrapper(openTag: string, tag: string): boolean {
  const attrs = openTag
    .replace(/^<\s*[a-zA-Z][\w:-]*/i, '')
    .replace(/\/?>$/, '')
    .trim()
  if (!attrs) return true
  return tag === 'section' && /^class\s*=\s*(["']?)pd-block\1$/i.test(attrs)
}

/**
 * HTML → 区块列表。
 *
 * <p>解析策略是「逐层切节点」：认得的标签变成结构化区块，容器（section/div）往里递归，
 * 认不得的整段原样变成 {@code html} 区块。因此**任何输入都不会丢内容**。</p>
 */
export function parseDetailHtml(html: string | null | undefined): DetailBlock[] {
  return parseNodes((html ?? '').trim(), { value: 0 })
}

/**
 * 解析一段 HTML。
 *
 * <p>★ 计数器要在递归里**共用**：每层各起一个计数器会让嵌套的 section 都从 `b1` 开始，
 * 出来的区块 id 重复 —— Vue 的 `:key` 撞车，删除/上移一块会连带另一块跟着动。</p>
 */
function parseNodes(source: string, counter: { value: number }): DetailBlock[] {
  const blocks: DetailBlock[] = []
  if (!source) return blocks

  const nextId = () => `b${(counter.value += 1)}`
  /** 顶层散落的、无法归入某个元素的文本 */
  let pendingText = ''

  const flushText = () => {
    const text = pendingText.trim()
    pendingText = ''
    if (text) blocks.push({ id: nextId(), type: 'html', text })
  }

  let cursor = 0
  while (cursor < source.length) {
    const lt = source.indexOf('<', cursor)
    if (lt < 0) {
      pendingText += source.slice(cursor)
      break
    }
    if (lt > cursor) pendingText += source.slice(cursor, lt)

    const openMatch = /^<\s*([a-zA-Z][\w:-]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>/.exec(source.slice(lt))
    if (!openMatch) {
      // 不是合法标签起始（比如正文里的裸 `<`），按文本处理
      pendingText += source.slice(lt, lt + 1)
      cursor = lt + 1
      continue
    }

    const tag = openMatch[1].toLowerCase()
    const openTag = openMatch[0]

    if (VOID_TAGS.has(tag)) {
      flushText()
      const block = toBlock(tag, '', openTag, openTag, nextId())
      if (block) blocks.push(block)
      cursor = lt + openTag.length
      continue
    }

    const element = readElement(source, lt, tag)
    if (!element) {
      // 没有闭合标签：剩下的全部原样保留
      pendingText += source.slice(lt)
      break
    }

    flushText()
    const raw = source.slice(lt, element.end)
    if (CONTAINER_TAGS.has(tag) && isTransparentWrapper(openTag, tag)) {
      blocks.push(...parseNodes(element.inner, counter))
    } else {
      const block = toBlock(tag, element.inner, raw, openTag, nextId())
      if (block) blocks.push(block)
      else blocks.push({ id: nextId(), type: 'html', text: raw })
    }
    cursor = element.end
  }

  flushText()
  return blocks
}

/** 图片 / 分割线 / 源码块是独立节点，不参与 section 分组 */
function serializeStandalone(block: DetailBlock): string {
  switch (block.type) {
    case 'image': {
      const url = block.url.trim()
      return url ? `<img src="${escapeAttr(url)}" alt="${escapeAttr(block.alt)}">` : ''
    }
    case 'divider':
      return '<hr>'
    case 'html':
      return block.text
    default:
      return ''
  }
}

/**
 * 区块列表 → HTML（拼回 `product.detail`）。
 *
 * <p><b>按「标题 + 紧跟的内容」分组进同一个 {@code <section class="pd-block">}</b>，
 * 与 V23 迁移生成的形态保持一致。这不是为了好看 —— 后台打开商品改个价格再保存，
 * 详情 HTML 会原样写回；如果一块一个 section，哪怕一个字没改，整段 HTML 也会被重写，
 * diff 里全是噪声，也更容易掩盖真正的改动。</p>
 */
export function serializeDetailBlocks(blocks: readonly DetailBlock[]): string {
  const parts: string[] = []
  /** 当前 section 的内部 HTML */
  let buffer: string[] = []

  const flush = () => {
    if (!buffer.length) return
    parts.push(`<section class="pd-block">${buffer.join('')}</section>`)
    buffer = []
  }

  for (const block of blocks) {
    switch (block.type) {
      case 'heading': {
        // 标题开启一个新 section
        flush()
        if (block.text.trim()) buffer.push(`<h3>${inlineText(block.text)}</h3>`)
        break
      }
      case 'paragraph': {
        if (block.text.trim()) buffer.push(`<p>${inlineText(block.text)}</p>`)
        break
      }
      case 'list': {
        const items = block.items.filter((item) => item.trim())
        if (!items.length) break
        const lis = items.map((item) => `<li>${inlineText(item)}</li>`).join('')
        buffer.push(`<ul>${lis}</ul>`)
        break
      }
      case 'params': {
        const rows = block.rows.filter((row) => row.label.trim() || row.value.trim())
        if (!rows.length) break
        const trs = rows
          .map((row) => `<tr><th>${inlineText(row.label)}</th><td>${inlineText(row.value)}</td></tr>`)
          .join('')
        buffer.push(`<table>${trs}</table>`)
        break
      }
      default: {
        flush()
        const html = serializeStandalone(block)
        if (html) parts.push(html)
      }
    }
  }
  flush()
  return parts.join('')
}

/** 新建一个空区块（id 只用于前端渲染 key，不参与序列化） */
export function createDetailBlock(type: DetailBlockType, id: string): DetailBlock {
  switch (type) {
    case 'heading':
      return { id, type: 'heading', text: '' }
    case 'paragraph':
      return { id, type: 'paragraph', text: '' }
    case 'list':
      return { id, type: 'list', items: [''] }
    case 'params':
      return { id, type: 'params', rows: [{ label: '', value: '' }] }
    case 'image':
      return { id, type: 'image', url: '', alt: '' }
    case 'divider':
      return { id, type: 'divider' }
    default:
      return { id, type: 'html', text: '' }
  }
}
