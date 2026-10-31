/** 金额格式化：分转元并保留两位小数。 */
export function formatPrice(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '0.00'
  const num = typeof value === 'string' ? Number(value) : value
  if (Number.isNaN(num)) return '0.00'
  return num.toFixed(2)
}

/** 大数字缩写：12800 -> 1.28万。 */
export function formatCount(value: number | null | undefined): string {
  if (!value) return '0'
  if (value < 10000) return String(value)
  return (value / 10000).toFixed(2) + '万'
}

/** 手机号脱敏：138****0000。 */
export function maskPhone(phone: string | null | undefined): string {
  if (!phone || phone.length < 7) return phone ?? ''
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

/**
 * 生成秒级倒计时文案。
 * @param targetMs 目标时间戳（毫秒）
 * @param nowMs 当前时间戳（毫秒）
 */
export function formatCountdown(targetMs: number, nowMs: number = Date.now()): string {
  let diff = Math.max(0, Math.floor((targetMs - nowMs) / 1000))
  const hours = Math.floor(diff / 3600)
  diff -= hours * 3600
  const minutes = Math.floor(diff / 60)
  const seconds = diff - minutes * 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
}

/** 商品详情的区块模型（后台可视化编辑 ⇄ `product.detail` HTML） */
export * from './detail-blocks'
