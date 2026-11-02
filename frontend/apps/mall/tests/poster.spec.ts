import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderPoster } from '@/utils/poster'

/**
 * 海报绘制的回归护栏。
 *
 * <p><b>为什么值得测</b>：`object-fit: cover` 的绘制尺寸一定**大于**图片区域，
 * canvas 又不像 `<img>` 那样自动按盒子裁剪。漏掉 `clip()` 时，竖图会向下溢出
 * 90~290px，把商品名/价格/二维码压住（线上真实故障：800×1000 的竖图溢 94px）。
 * 这类问题在截图之外看不出来，只能用「绘制顺序」来断言。</p>
 *
 * <p><b>怎么测</b>：node 环境没有 canvas，用一个记录调用序列的 Proxy 假上下文 +
 * 假 Image 顶替，断言 `rect(图片区域) → clip() → drawImage` 的顺序。</p>
 */
const WIDTH = 750
const IMAGE_HEIGHT = 750

type Op = { name: string; args: unknown[] }

/** 记录所有方法调用的 2D 上下文假实现（属性读写照常，方法只记录） */
function createFakeContext() {
  const ops: Op[] = []
  const props: Record<string, unknown> = {}
  const ctx = new Proxy(props, {
    get(target, key: string) {
      if (key in target) return target[key]
      return (...args: unknown[]) => {
        ops.push({ name: key, args })
        // wrapLines 依赖 measureText；宽度给个固定值即可（本用例只关心图片区域）
        return key === 'measureText' ? { width: 10 } : undefined
      }
    },
    set(target, key: string, value) {
      target[key] = value
      return true
    },
  })
  return { ctx, ops }
}

/** 装好 document / Image 假环境，返回记录下来的调用序列 */
function setup(imageSize: { width: number; height: number }) {
  const { ctx, ops } = createFakeContext()
  const canvas = {
    width: 0,
    height: 0,
    getContext: () => ctx,
    toDataURL: () => 'data:image/png;base64,AAAA',
  }
  vi.stubGlobal('document', { createElement: () => canvas })

  class FakeImage {
    width = imageSize.width
    height = imageSize.height
    onload: (() => void) | null = null
    onerror: (() => void) | null = null
    set src(_value: string) {
      // loadImage 在 await 里等 onload，这里异步触发
      void Promise.resolve().then(() => this.onload?.())
    }
  }
  vi.stubGlobal('Image', FakeImage)

  return { ops }
}

function render() {
  return renderPoster({
    title: '篮球实战鞋 黑白红',
    price: 999,
    imageUrl: '/img/shoes/shoe-02.jpg',
    shareUrl: 'https://m.tickcart.demo/products/6',
  })
}

describe('商品海报绘制', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('竖图 cover 溢出图片区域时必须先裁剪，否则压住商品名与价格', async () => {
    const { ops } = setup({ width: 800, height: 1000 })
    await render()

    const drawIndex = ops.findIndex((op) => op.name === 'drawImage')
    expect(drawIndex).toBeGreaterThan(-1)

    // 前提：cover 的绘制高度确实超过图片区域（800×1000 → 937.5），否则本用例没有意义
    const drawHeight = ops[drawIndex].args[4] as number
    expect(drawHeight).toBeGreaterThan(IMAGE_HEIGHT)

    // 关键断言：裁剪矩形正好是图片区域，且 clip() 在 drawImage 之前
    const rectIndex = ops.findIndex((op) => op.name === 'rect')
    const clipIndex = ops.findIndex((op) => op.name === 'clip')
    expect(rectIndex).toBeGreaterThan(-1)
    expect(ops[rectIndex].args).toEqual([0, 0, WIDTH, IMAGE_HEIGHT])
    expect(clipIndex).toBeGreaterThan(rectIndex)
    expect(clipIndex).toBeLessThan(drawIndex)

    // 裁剪必须包在 save/restore 里，不能污染后面的文字绘制
    const saveIndex = ops.findIndex((op) => op.name === 'save')
    const restoreIndex = ops.findIndex((op) => op.name === 'restore')
    expect(saveIndex).toBeLessThan(rectIndex)
    expect(restoreIndex).toBeGreaterThan(drawIndex)
  })

  it('方图不溢出，但同样走裁剪（两种分支行为一致）', async () => {
    const { ops } = setup({ width: 900, height: 900 })
    await render()

    const drawIndex = ops.findIndex((op) => op.name === 'drawImage')
    expect(ops[drawIndex].args[4]).toBeCloseTo(IMAGE_HEIGHT)
    expect(ops.findIndex((op) => op.name === 'clip')).toBeLessThan(drawIndex)
  })

  it('图片加载失败降级成色块，不让整张海报失败', async () => {
    const { ops } = setup({ width: 800, height: 1000 })
    vi.stubGlobal(
      'Image',
      class {
        onload: (() => void) | null = null
        onerror: (() => void) | null = null
        set src(_value: string) {
          void Promise.resolve().then(() => this.onerror?.())
        }
      },
    )

    await expect(render()).resolves.toBe('data:image/png;base64,AAAA')
    expect(ops.some((op) => op.name === 'drawImage')).toBe(false)
    expect(ops.some((op) => op.name === 'fillText' && op.args[0] === '商品图片加载失败')).toBe(true)
  })
})
