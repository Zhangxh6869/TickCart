import { describe, expect, it } from 'vitest'
import {
  createDetailBlock,
  parseDetailHtml,
  serializeDetailBlocks,
  type DetailBlock,
} from '@tickcart/shared-utils'

/**
 * 商品详情「区块模型」的护栏。
 *
 * <p>这套断言守的是两件事：</p>
 * <ol>
 *   <li><b>往返不丢内容</b>：后台把详情读成区块、改完再写回，认不出的部分必须原样保留。
 *       这条一旦破，运营随手存一次就可能把商品详情吃掉一半。</li>
 *   <li><b>产物落在商城端白名单内</b>：序列化只允许产出 section/h3/p/ul/li/table/img/hr，
 *       否则会被 `rich-text.ts` 清洗掉，出现「后台存进去了、前台不显示」。</li>
 * </ol>
 *
 * <p>区块里的 {@code id} 只给前端做渲染 key，比对时统一剥掉。</p>
 */
const strip = (blocks: DetailBlock[]) => blocks.map(({ id, ...rest }) => rest)

/** 迁移 V23 生成的真实结构：section 里 h3 + ul，后面跟一张图 */
const GENERATED =
  '<section class="pd-block"><h3>产品亮点</h3><ul><li>绵密泡沫</li><li>敏感肌适用</li></ul></section>' +
  '<img src="/img/skincare/201-1.jpg" alt="氨基酸温和洁面乳">' +
  '<section class="pd-block"><h3>规格参数</h3><table>' +
  '<tr><th>规格</th><td>150ml / 250ml</td></tr>' +
  '<tr><th>适用肤质</th><td>各种肤质</td></tr></table></section>' +
  '<section class="pd-block"><h3>使用与保养</h3><p>取适量于掌心。</p></section>'

describe('parseDetailHtml', () => {
  it('把 section 里的 h3 / ul / table / p 拆成结构化区块', () => {
    expect(strip(parseDetailHtml(GENERATED))).toEqual([
      { type: 'heading', text: '产品亮点' },
      { type: 'list', items: ['绵密泡沫', '敏感肌适用'] },
      { type: 'image', url: '/img/skincare/201-1.jpg', alt: '氨基酸温和洁面乳' },
      { type: 'heading', text: '规格参数' },
      {
        type: 'params',
        rows: [
          { label: '规格', value: '150ml / 250ml' },
          { label: '适用肤质', value: '各种肤质' },
        ],
      },
      { type: 'heading', text: '使用与保养' },
      { type: 'paragraph', text: '取适量于掌心。' },
    ])
  })

  it('区块 id 全局唯一（嵌套容器不会各自从 b1 重新编号）', () => {
    const blocks = parseDetailHtml(
      '<section><div><p>a</p></div><p>b</p></section><section><p>c</p></section>',
    )
    const ids = blocks.map((block) => block.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('认不出的标签整段保留成 html 区块，不丢内容', () => {
    const html = '<div class="hero"><span>促销</span></div>'
    const blocks = parseDetailHtml(html)
    expect(blocks).toHaveLength(1)
    expect(blocks[0]).toMatchObject({ type: 'html', text: html })
  })

  it('段落里含行内标签时整段退化为 html，不把格式抹平', () => {
    const blocks = parseDetailHtml('<p>只要 <strong>今天</strong>下单</p>')
    expect(blocks[0].type).toBe('html')
    expect(serializeDetailBlocks(blocks)).toBe('<p>只要 <strong>今天</strong>下单</p>')
  })

  it('空内容返回空数组', () => {
    expect(parseDetailHtml('')).toEqual([])
    expect(parseDetailHtml(null)).toEqual([])
    expect(parseDetailHtml('   ')).toEqual([])
  })

  it('把 <br> 读成换行，段落里的多行不会丢', () => {
    const blocks = parseDetailHtml('<p>第一行<br>第二行</p>')
    expect(blocks[0]).toMatchObject({ type: 'paragraph', text: '第一行\n第二行' })
  })

  it('没有 src 的图片不生成空图区块', () => {
    expect(parseDetailHtml('<img alt="x">')).toEqual([])
  })
})

describe('serializeDetailBlocks', () => {
  it('按区块顺序拼回 HTML，结构落在商城白名单内', () => {
    const blocks = parseDetailHtml(GENERATED)
    const html = serializeDetailBlocks(blocks)
    expect(html).toContain('<section class="pd-block"><h3>产品亮点</h3>')
    expect(html).toContain('<li>绵密泡沫</li>')
    expect(html).toContain('<tr><th>规格</th><td>150ml / 250ml</td></tr>')
    expect(html).toContain('<img src="/img/skincare/201-1.jpg" alt="氨基酸温和洁面乳">')
    // 只允许白名单标签
    const tags = new Set([...html.matchAll(/<\/?([a-zA-Z][\w-]*)/g)].map((m) => m[1].toLowerCase()))
    for (const tag of tags) {
      expect(['section', 'h3', 'p', 'ul', 'li', 'table', 'tr', 'th', 'td', 'img', 'hr', 'br']).toContain(tag)
    }
  })

  it('HTML → 区块 → HTML → 区块 是稳定的（再解析一次结果不变）', () => {
    const once = parseDetailHtml(GENERATED)
    const html = serializeDetailBlocks(once)
    const twice = parseDetailHtml(html)
    expect(strip(twice)).toEqual(strip(once))
    expect(serializeDetailBlocks(twice)).toBe(html)
  })

  it('文本里的尖括号与 & 会被转义，不会注入标签', () => {
    const html = serializeDetailBlocks([
      { id: 'x', type: 'paragraph', text: '5 < 8 & 10 > 8' },
    ])
    expect(html).toBe('<section class="pd-block"><p>5 &lt; 8 &amp; 10 &gt; 8</p></section>')
  })

  it('换行序列化成 <br>', () => {
    expect(serializeDetailBlocks([{ id: 'x', type: 'paragraph', text: 'a\nb' }])).toBe(
      '<section class="pd-block"><p>a<br>b</p></section>',
    )
  })

  it('空区块不产出空壳标签', () => {
    expect(
      serializeDetailBlocks([
        { id: 'a', type: 'heading', text: '  ' },
        { id: 'b', type: 'list', items: ['', ' '] },
        { id: 'c', type: 'params', rows: [{ label: '', value: '' }] },
        { id: 'd', type: 'image', url: '', alt: '' },
      ]),
    ).toBe('')
  })

  it('分割线序列化成 hr', () => {
    expect(serializeDetailBlocks([{ id: 'x', type: 'divider' }])).toBe('<hr>')
  })
})

describe('createDetailBlock', () => {
  it('列表与参数表各带一行空行，避免新建后没有输入框', () => {
    expect(createDetailBlock('list', 'n1')).toMatchObject({ type: 'list', items: [''] })
    expect(createDetailBlock('params', 'n2')).toMatchObject({
      type: 'params',
      rows: [{ label: '', value: '' }],
    })
  })
})
