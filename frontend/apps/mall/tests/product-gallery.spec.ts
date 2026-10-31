import { describe, expect, it } from 'vitest'
import { buildGallery } from '../src/utils/product-gallery'

/**
 * 图集组装规则的护栏。
 *
 * <p>这套断言守的是「列表对同一件商品恒定」这件事 —— 切款式时只允许换下标，
 * 不允许换列表。列表一重排，缩略图会整体错位，主图和高亮也很容易对不上。</p>
 */
describe('buildGallery', () => {
  it('按图集顺序铺开，规格图追加在后面', () => {
    expect(buildGallery(['/img/a.jpg', '/img/b.jpg'], '/img/main.jpg', ['/img/sku1.jpg'])).toEqual([
      '/img/a.jpg',
      '/img/b.jpg',
      '/img/sku1.jpg',
    ])
  })

  it('图集为空时退回主图', () => {
    expect(buildGallery([], '/img/main.jpg', [])).toEqual(['/img/main.jpg'])
  })

  it('主图也没有时返回空数组（页面走「暂无图片」）', () => {
    expect(buildGallery([], null, [null, ''])).toEqual([])
  })

  it('规格图与图集重复时不重复插入', () => {
    expect(buildGallery(['/img/a.jpg', '/img/b.jpg'], null, ['/img/b.jpg', '/img/a.jpg'])).toEqual([
      '/img/a.jpg',
      '/img/b.jpg',
    ])
  })

  it('多个规格图按传入顺序追加，且互不重复', () => {
    expect(
      buildGallery(['/img/main.jpg'], null, ['/img/sku-b.jpg', '/img/sku-a.jpg', '/img/sku-b.jpg']),
    ).toEqual(['/img/main.jpg', '/img/sku-b.jpg', '/img/sku-a.jpg'])
  })

  it('同样的输入永远得到同样的列表（切款式不会重排）', () => {
    const images = ['/img/a.jpg', '/img/b.jpg']
    const skus = ['/img/sku1.jpg', '/img/sku2.jpg']
    expect(buildGallery(images, null, skus)).toEqual(buildGallery(images, null, skus))
  })

  it('过滤掉图集里的空串，不留下破图占位', () => {
    expect(buildGallery(['/img/a.jpg', '', null as unknown as string], null, [])).toEqual(['/img/a.jpg'])
  })
})
