import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/**
 * 待付款订单的支付倒计时。
 *
 * <p>收银台、订单详情、订单列表三处要显示同一个倒计时，逻辑收敛在这里，
 * 避免三份各写一遍（尤其是 mm:ss 格式化与「已过期」的边界）。</p>
 *
 * <p>时钟是**模块级共享**的：订单列表里每张待付款卡片都各起一个 setInterval
 * 会白白多出 N 个定时器，所以统一用 {@link useNow}，谁用谁订阅。</p>
 *
 * <p>时限与后端 {@code tickcart.order.timeout-ttl-ms}（默认 900000ms）保持一致 ——
 * 前端拿不到这个配置，只能写死，改后端超时时间时这里要一起改。</p>
 */

/** 待付款订单的支付时限（毫秒） */
export const PAY_WINDOW_MS = 15 * 60 * 1000

/** 共享时钟的当前时间戳 */
const now = ref(Date.now())

let timer: ReturnType<typeof setInterval> | null = null
let subscribers = 0

/**
 * 获取每秒跳一次的共享时钟。
 *
 * <p>必须在组件 setup 中调用：依赖 onMounted / onBeforeUnmount 起停定时器，
 * 最后一个使用者卸载后定时器才会停。</p>
 */
export function useNow(): Ref<number> {
  onMounted(() => {
    subscribers += 1
    if (timer === null) {
      now.value = Date.now()
      timer = setInterval(() => {
        now.value = Date.now()
      }, 1000)
    }
  })

  onBeforeUnmount(() => {
    subscribers -= 1
    if (subscribers <= 0 && timer !== null) {
      clearInterval(timer)
      timer = null
    }
  })

  return now
}

/**
 * 由下单时间算剩余毫秒。
 *
 * <p>已过期或时间非法一律返回 0（不返回负数），调用方不必再夹一次。</p>
 */
export function remainMsOf(createdAt: string | null | undefined, current: number): number {
  if (!createdAt) return 0
  const placedAt = new Date(createdAt.replace(' ', 'T')).getTime()
  if (Number.isNaN(placedAt)) return 0
  return Math.max(0, placedAt + PAY_WINDOW_MS - current)
}

/** 剩余毫秒 → {@code mm:ss} */
export function formatCountdown(remainMs: number): string {
  const total = Math.max(0, Math.floor(remainMs / 1000))
  const mm = String(Math.floor(total / 60)).padStart(2, '0')
  const ss = String(total % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

/** 单个订单的支付倒计时（收银台 / 订单详情这类「一屏只有一个订单」的页面用）。 */
export function usePayCountdown(createdAt: Ref<string | null | undefined>) {
  const current = useNow()
  const remainMs = computed(() => remainMsOf(createdAt.value, current.value))

  return {
    remainMs,
    countdown: computed(() => formatCountdown(remainMs.value)),
    expired: computed(() => remainMs.value <= 0),
  }
}
