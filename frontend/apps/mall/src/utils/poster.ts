/**
 * 商品分享海报的 Canvas 绘制。
 *
 * <p>与组件解耦（不碰 Vue），便于复用与单独调试；组件只负责「拿到 dataURL 后展示」。</p>
 *
 * <p><b>两个必须守住的点：</b></p>
 * <ol>
 *   <li>商品图必须**同源**（本项目全部走 {@code /img/**}），否则 canvas 被污染、
 *       {@code toDataURL()} 抛 SecurityError。将来接外链图要补 {@code crossOrigin} + CORS。</li>
 *   <li>图片加载失败只降级成色块，**不能让整张海报失败**。</li>
 * </ol>
 */
import qrcode from 'qrcode-generator'
import { formatPrice } from '@tickcart/shared-utils'

/** 画布尺寸：2 倍图，390 宽的手机上看足够清晰 */
const WIDTH = 750
const HEIGHT = 1200
const PAD = 40
/** 商品图区域高度 */
const IMAGE_HEIGHT = 750
/** 二维码区域边长 */
const QR_BOX = 200
/** 二维码四周留白（单位：模块数）—— 没有静默区扫码识别率会明显下降 */
const QR_QUIET = 2
const RED = '#ee0a24'
const INK = '#323233'
const GRAY = '#969799'
const FONT = '"PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif'

export interface PosterInput {
  title: string
  price: number
  /** 商品主图地址（相对 / 绝对均可） */
  imageUrl: string | null
  /** 二维码指向的地址（即分享链接） */
  shareUrl: string
}

function loadImage(src: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const image = new Image()
    image.onload = () => resolve(image)
    // 图挂了不能让整张海报失败，交给调用方退化成色块
    image.onerror = () => resolve(null)
    image.src = src
  })
}

/** 等比裁剪铺满（`object-fit: cover` 的手写版） */
function drawCover(
  ctx: CanvasRenderingContext2D,
  img: HTMLImageElement,
  x: number,
  y: number,
  w: number,
  h: number
) {
  const scale = Math.max(w / img.width, h / img.height)
  const drawW = img.width * scale
  const drawH = img.height * scale
  ctx.drawImage(img, x + (w - drawW) / 2, y + (h - drawH) / 2, drawW, drawH)
}

/**
 * 按宽度折行；超出 maxLines 时把最后一行尾部换成省略号。
 * Canvas 没有 CSS 那样的 text-overflow，只能自己按 measureText 逐字量。
 */
function wrapLines(
  ctx: CanvasRenderingContext2D,
  text: string,
  maxWidth: number,
  maxLines: number
): string[] {
  const lines: string[] = []
  let current = ''
  let consumed = 0

  for (const ch of text) {
    const next = current + ch
    if (current && ctx.measureText(next).width > maxWidth) {
      lines.push(current)
      consumed += current.length
      current = ch
      if (lines.length === maxLines) break
    } else {
      current = next
    }
  }

  if (lines.length < maxLines) {
    if (current) lines.push(current)
    return lines
  }

  // 行数已满且原文还有剩余 → 最后一行末尾加省略号
  if (consumed < text.length) {
    let last = lines[maxLines - 1]
    while (last && ctx.measureText(`${last}…`).width > maxWidth) last = last.slice(0, -1)
    lines[maxLines - 1] = `${last}…`
  }
  return lines
}

/**
 * 画二维码。
 *
 * <p>用 {@code qrcode-generator} 的 {@code renderTo2dContext} 直接画到海报画布上 ——
 * 不生成中间图片、不经过 img 解码，也就不存在 canvas 污染的问题；cellSize 取整还能保证不糊。</p>
 */
function drawQrcode(ctx: CanvasRenderingContext2D, url: string, x: number, y: number) {
  const qr = qrcode(0, 'M')
  qr.addData(url)
  qr.make()

  const total = qr.getModuleCount() + QR_QUIET * 2
  const cell = Math.max(2, Math.floor(QR_BOX / total))
  const size = cell * total
  const left = x + Math.round((QR_BOX - size) / 2)
  const top = y + Math.round((QR_BOX - size) / 2)

  ctx.save()
  ctx.fillStyle = '#fff'
  ctx.fillRect(left, top, size, size)
  // 静默区：整体内缩 QR_QUIET 个模块再画
  ctx.translate(left + QR_QUIET * cell, top + QR_QUIET * cell)
  qr.renderTo2dContext(ctx, cell)
  ctx.restore()
}

/** 生成海报，返回 PNG 的 dataURL。 */
export async function renderPoster(input: PosterInput): Promise<string> {
  const canvas = document.createElement('canvas')
  canvas.width = WIDTH
  canvas.height = HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('当前环境不支持 Canvas，无法生成海报')

  const productImage = input.imageUrl ? await loadImage(input.imageUrl) : null

  // 底色 + 商品图
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, WIDTH, HEIGHT)
  if (productImage) {
    drawCover(ctx, productImage, 0, 0, WIDTH, IMAGE_HEIGHT)
  } else {
    ctx.fillStyle = '#f2f3f5'
    ctx.fillRect(0, 0, WIDTH, IMAGE_HEIGHT)
    ctx.fillStyle = GRAY
    ctx.font = `28px ${FONT}`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText('商品图片加载失败', WIDTH / 2, IMAGE_HEIGHT / 2)
    ctx.textAlign = 'left'
  }

  // 标题（最多两行）
  ctx.textBaseline = 'top'
  ctx.fillStyle = INK
  ctx.font = `700 34px ${FONT}`
  wrapLines(ctx, input.title, WIDTH - PAD * 2, 2).forEach((line, index) => {
    ctx.fillText(line, PAD, 796 + index * 46)
  })

  // 价格：¥ 与小数用小字号，整数用大字号（与详情页版式一致）
  const [integer, decimal] = formatPrice(input.price).split('.')
  const priceBaseline = 960
  ctx.textBaseline = 'alphabetic'
  ctx.fillStyle = RED
  ctx.font = `700 30px ${FONT}`
  ctx.fillText('¥', PAD, priceBaseline)
  const symbolWidth = ctx.measureText('¥').width
  ctx.font = `700 60px ${FONT}`
  ctx.fillText(integer, PAD + symbolWidth + 2, priceBaseline)
  const integerWidth = ctx.measureText(integer).width
  ctx.font = `700 30px ${FONT}`
  ctx.fillText(`.${decimal}`, PAD + symbolWidth + integerWidth + 2, priceBaseline)

  // 二维码 + 说明
  const qrX = WIDTH - PAD - QR_BOX
  const qrY = HEIGHT - PAD - 34 - QR_BOX
  drawQrcode(ctx, input.shareUrl, qrX, qrY)
  ctx.fillStyle = GRAY
  ctx.font = `24px ${FONT}`
  ctx.textAlign = 'center'
  ctx.fillText('长按识别二维码', qrX + QR_BOX / 2, HEIGHT - PAD)
  ctx.textAlign = 'left'

  // 左下角品牌
  ctx.fillStyle = INK
  ctx.font = `700 28px ${FONT}`
  ctx.fillText('TickCart 秒杀商城', PAD, HEIGHT - PAD - 34)
  ctx.fillStyle = GRAY
  ctx.font = `22px ${FONT}`
  ctx.fillText('分享好物，一起拼单', PAD, HEIGHT - PAD)

  return canvas.toDataURL('image/png')
}
