<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { showToast } from 'vant'
import ShareGuide from '@/components/ShareGuide.vue'
import SharePoster from '@/components/SharePoster.vue'
import {
  buildProductShareUrl,
  copyText,
  isWechatBrowser,
  openShareChannel,
  toAbsoluteUrl,
  type ShareAction,
  type ShareChannel,
  type ShareGuideMode,
  type ShareProduct,
  type ShareTarget,
} from '@/utils/share'

/**
 * 商品分享面板（「分享至」）。
 *
 * <p>面板本身用 Vant 内置的 {@code van-share-sheet}：它的品牌图标（微信 / 朋友圈 / QQ / 微博）
 * 自带品牌色圆底，省掉一整套图片素材；{@code link} / {@code poster} 还会自动映射到
 * {@code link-o} / {@code photo-o}。QQ空间 没有内置图标，用星形 + 自定义配色顶替。</p>
 *
 * <p>本组件是「分享」这件事的唯一出口：面板、引导蒙层、海报、举报、反馈都在这里，
 * 外部页面只需要 {@code <ShareSheet v-model:show="x" :product="p" />}。</p>
 */
const props = defineProps<{ show: boolean; product: ShareProduct }>()
const emit = defineEmits<{ 'update:show': [boolean] }>()

interface ShareOption {
  /** 动作标识，与展示名解耦：文案会改，动作不能跟着改 */
  key: ShareAction
  name: string
  icon: string
  className?: string
}

/** 第一行：社交渠道 */
const CHANNEL_OPTIONS: ShareOption[] = [
  { key: 'wechat', name: '微信', icon: 'wechat' },
  { key: 'moments', name: '朋友圈', icon: 'wechat-moments' },
  { key: 'qq', name: 'QQ', icon: 'qq' },
  { key: 'qzone', name: 'QQ空间', icon: 'star-o', className: 'share-opt--qzone' },
  { key: 'weibo', name: '新浪微博', icon: 'weibo' },
]

/** 第二行：站内动作 */
const ACTION_OPTIONS: ShareOption[] = [
  { key: 'link', name: '复制链接', icon: 'link', className: 'share-opt--plain' },
  { key: 'poster', name: '生成海报', icon: 'poster', className: 'share-opt--plain' },
  { key: 'report', name: '举报', icon: 'warning-o', className: 'share-opt--plain' },
  { key: 'feedback', name: '反馈与建议', icon: 'edit', className: 'share-opt--plain' },
  { key: 'more', name: '更多', icon: 'ellipsis', className: 'share-opt--plain' },
]

/**
 * ★ 传「二维数组」而不是扁平数组 —— Vant 的 ShareSheet 会把每个子数组渲染成**独立的一行**
 * （各自一个 `.van-share-sheet__options`，自带 `overflow-x: auto`）。
 * 这样「左右滑动」的能力天然落在行上，面板本身不需要、也不应该横向滚动。
 */
const optionRows = [CHANNEL_OPTIONS, ACTION_OPTIONS]

const REPORT_REASONS = ['虚假宣传', '价格欺诈', '禁售商品', '其他']

const guideVisible = ref(false)
const guideMode = ref<ShareGuideMode>('wechat')
const posterVisible = ref(false)
const reportVisible = ref(false)
const feedbackVisible = ref(false)
const feedbackText = ref('')

const reportActions = REPORT_REASONS.map((name) => ({ name }))

const target = computed<ShareTarget>(() => ({
  url: buildProductShareUrl(props.product.id),
  title: props.product.name,
  image: toAbsoluteUrl(props.product.mainImage),
}))

const rootRef = ref<HTMLElement | null>(null)

/**
 * 两行选项各自横向滚动。
 *
 * <p>行容器由 Vant 内部渲染，拿不到模板 ref，只能在面板打开后从根节点查。这里只做两件事：</p>
 * <ol>
 *   <li>量出「这一行是否真的溢出」→ 溢出才挂 `is-scrollable`（右侧渐隐只在能滑时出现，
 *       正常宽度下 5 个正好铺满，视觉与之前完全一致）；</li>
 *   <li>滑到最右端挂 `is-at-end`，把右侧渐隐收掉。</li>
 * </ol>
 */
let rowEls: HTMLElement[] = []
let detachRows: Array<() => void> = []

function measureRows() {
  rowEls.forEach((row) => {
    const scrollable = row.scrollWidth > row.clientWidth + 1
    const atEnd = scrollable && row.scrollLeft + row.clientWidth >= row.scrollWidth - 1
    row.classList.toggle('is-scrollable', scrollable)
    row.classList.toggle('is-at-end', atEnd)
  })
}

function bindRows() {
  unbindRows()
  rowEls = Array.from(
    rootRef.value?.querySelectorAll<HTMLElement>('.van-share-sheet__options') ?? []
  )
  rowEls.forEach((row) => {
    const onScroll = () => measureRows()
    row.addEventListener('scroll', onScroll, { passive: true })
    detachRows.push(() => row.removeEventListener('scroll', onScroll))
  })
  measureRows()
}

function unbindRows() {
  detachRows.forEach((off) => off())
  detachRows = []
  rowEls = []
}

function onResize() {
  if (props.show) measureRows()
}

/**
 * 关面板 → 等它退场 → 再开下一层，避免两个弹层同时挂在屏幕上（Vant 出场约 300ms）。
 *
 * <p>★ 走这条路径的动作都**不能依赖用户手势**：{@code setTimeout} 会丢掉手势上下文，
 * 所以 {@code navigator.share} / {@code clipboard} 那两个是同步发起的，不走这里。</p>
 */
const LAYER_SWITCH_MS = 300
let layerTimer = 0
function switchLayer(open: () => void) {
  emit('update:show', false)
  window.clearTimeout(layerTimer)
  layerTimer = window.setTimeout(open, LAYER_SWITCH_MS)
}

window.addEventListener('resize', onResize)
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.clearTimeout(layerTimer)
  unbindRows()
})

watch(
  () => props.show,
  async (show) => {
    if (!show) {
      unbindRows()
      return
    }
    await nextTick()
    // 弹层是 lazyRender 的，DOM 要等这一帧之后才真正挂上
    window.requestAnimationFrame(bindRows)
  }
)

function close() {
  emit('update:show', false)
}

function onSelect(option: ShareOption) {
  switch (option.key) {
    case 'wechat':
    case 'moments':
      // H5 没有调起微信原生分享的能力，只能把用户引到右上角菜单
      openGuide(option.key)
      break
    case 'qq':
    case 'qzone':
    case 'weibo':
      if (isWechatBrowser()) {
        // 微信内置浏览器会拦截这些分享页域名（打开即白屏），引导改用外部浏览器
        openGuide('external')
      } else {
        openShareChannel(option.key as ShareChannel, target.value)
        close()
      }
      break
    case 'link':
      void copyLink()
      break
    case 'poster':
      switchLayer(() => {
        posterVisible.value = true
      })
      break
    case 'report':
      switchLayer(() => {
        reportVisible.value = true
      })
      break
    case 'feedback':
      feedbackText.value = ''
      switchLayer(() => {
        feedbackVisible.value = true
      })
      break
    case 'more':
      void shareNative()
      break
  }
}

function openGuide(mode: ShareGuideMode) {
  switchLayer(() => {
    guideMode.value = mode
    guideVisible.value = true
  })
}

/** 复制链接：先发起复制（保住用户手势），再关面板 */
async function copyLink() {
  const ok = await copyText(target.value.url)
  close()
  showToast(ok ? '链接已复制' : '复制失败，请长按链接手动复制')
}

/** 更多：交给系统分享面板（Web Share API），不支持时提示改用「复制链接」 */
async function shareNative() {
  if (typeof navigator.share !== 'function') {
    close()
    showToast('当前浏览器不支持系统分享，请使用「复制链接」')
    return
  }
  try {
    // ★ 必须在用户手势里同步调用：前面不能有 await
    await navigator.share({ title: target.value.title, url: target.value.url })
  } catch {
    // 用户主动取消（AbortError）不算失败，不提示
  }
  close()
}

function onReportSelect(action: { name: string }) {
  reportVisible.value = false
  showToast(`已提交举报：${action.name}`)
}

/** 反馈：内容为空时拦下，不让弹窗关掉 */
function beforeFeedbackClose(action: string) {
  if (action !== 'confirm') return true
  if (!feedbackText.value.trim()) {
    showToast('请填写反馈内容')
    return false
  }
  showToast('感谢反馈，我们会尽快处理')
  return true
}
</script>

<template>
  <div ref="rootRef" class="share">
    <van-share-sheet
      :show="show"
      title="分享至"
      cancel-text="取消"
      :options="optionRows"
      @update:show="(value: boolean) => emit('update:show', value)"
      @select="onSelect"
    />

    <ShareGuide v-model:show="guideVisible" :mode="guideMode" />
    <SharePoster v-model:show="posterVisible" :product="product" />

    <van-action-sheet
      v-model:show="reportVisible"
      title="选择举报原因"
      cancel-text="取消"
      :actions="reportActions"
      @select="onReportSelect"
    />

    <van-dialog
      v-model:show="feedbackVisible"
      title="反馈与建议"
      show-cancel-button
      :before-close="beforeFeedbackClose"
    >
      <div class="share__feedback">
        <van-field
          v-model="feedbackText"
          type="textarea"
          rows="4"
          maxlength="200"
          show-word-limit
          placeholder="说说你遇到的问题或建议…"
        />
      </div>
    </van-dialog>
  </div>
</template>

<style scoped>
/*
 * ★ 面板本身绝不横向滚动：横向滑动只允许发生在两行选项内部。
 * 这里既是「守卫」（万一将来有东西撑宽了，也是裁掉而不是让整块面板跟着动），
 * 也覆盖了「取消」按钮那个 width:100% + margin 的溢出（见下）。
 *
 * 底色用淡灰（Vant 的 --van-gray-2）：面板里有两个「白块」——「取消」按钮与第二行的
 * 圆形图标底 —— 面板若是白的，这两处就跟底色糊在一起看不出边界；铺成淡灰它们才浮得出来。
 */
.share :deep(.van-share-sheet) {
  overflow-x: hidden;
  background: var(--van-gray-2, #f2f3f5);
}

/*
 * ── 文字 ──
 * 面板内所有文字统一「正文黑」。Vant 默认口径不统一：选项名是 gray-7(#646566) 偏灰，
 * 标题与「取消」是 --van-text-color(#323233)。在淡灰底上灰字更糊，所以统一压到最深的正文黑。
 * ★ 本项目从不用纯 #000 —— 正文黑统一是 #323233（App.vue / ProductCard.vue / CartView.vue 同款）。
 */
.share :deep(.van-share-sheet__title),
.share :deep(.van-share-sheet__name),
.share :deep(.van-share-sheet__cancel) {
  color: #323233;
}

/* 标题「分享至」加大加粗（Vant 默认 14px / 400，在面板里存在感太弱） */
.share :deep(.van-share-sheet__title) {
  font-size: 17px;
  font-weight: var(--van-font-bold, 600);
}

/* ── 两行选项：各自横向滚动 ── */
.share :deep(.van-share-sheet__options) {
  padding: 16px 0 4px;
  /* 行滑到头之后不要把滑动传给面板或页面 */
  overscroll-behavior-x: contain;
}

/* Vant 会给第二行加一条内缩分隔线；参考图里两行之间只有留白 */
.share :deep(.van-share-sheet__options--border)::before {
  display: none;
}

/*
 * 固定项宽：宽屏按 20% 铺满，窄屏锁 78px。
 * 390 宽下 20% 正好 78px → 5 个铺满、滑不动（与参考图一致）；
 * 窄屏或以后加到 6 个以上时行内容超宽 → 该行自然可横向滑动。
 */
.share :deep(.van-share-sheet__option) {
  flex: 0 0 20%;
  min-width: 78px;
}

/* Vant 默认给图标左右各 16px 外边距，会把 48px 图标挤出栅格 */
.share :deep(.van-share-sheet__icon),
.share :deep(.van-share-sheet__image-icon) {
  margin: 0;
}

/*
 * 右侧边缘渐隐：只在「这一行真的能滑」且「还没滑到头」时出现。
 * 用 sticky 钉在滚动视口右边缘 —— 行内容不够宽时整个伪元素不渲染，视觉零变化。
 */
.share :deep(.van-share-sheet__options.is-scrollable:not(.is-at-end))::after {
  content: '';
  position: sticky;
  right: 0;
  flex: 0 0 28px;
  margin-left: -28px;
  align-self: stretch;
  pointer-events: none;
  background: linear-gradient(to right, rgba(255, 255, 255, 0), #fff);
}

/*
 * 第二行（复制链接 / 生成海报 / 举报 / 反馈与建议 / 更多）在参考图里是「白底 + 细描边」。
 * 只作用于带 .share-opt--plain 的选项 —— 不这么限定的选择器权重会压过 Vant 自己的
 * .van-share-sheet__icon--wechat 等品牌色规则，把品牌图标也刷成白色。
 */
.share :deep(.share-opt--plain .van-share-sheet__icon) {
  border: 1px solid var(--van-border-color);
  background-color: #fff;
  color: var(--van-gray-7);
  font-size: 24px;
  box-sizing: border-box;
}
/* QQ空间：Vant 没有内置图标，用星形配黄色圆底复刻参考图 */
.share :deep(.share-opt--qzone .van-share-sheet__icon) {
  background-color: #f7b500;
  color: #fff;
  font-size: 30px;
}

/*
 * ★ 宽度必须显式算出来，不能用 Vant 的 `width: 100%`，也不能图省事写 `auto`：
 *   - Vant 默认 `width: 100%` 是「内容宽 = 容器宽」，**不会**扣掉下面这两条 margin，
 *     两者相加会让按钮右端越界 24px，把整个 popup 变成横向可滚动容器 ——
 *     表现就是「弹窗整体会跟着左右移动」；
 *   - 改成 `auto` 也不行：`<button>` 是 shrink-to-fit，会塌成文字宽（实测只剩 32px）。
 *   `calc(100% - 24px)` 才是「左右各留 12px 的整宽块」。
 *
 * 底色显式写白（Vant 默认取 --van-background-2 也是白，这里写明是为了不受主题改动影响）：
 * 面板已是淡灰，这块白就是参考图里那个「取消」白块。
 */
.share :deep(.van-share-sheet__cancel) {
  width: calc(100% - 24px);
  margin: 12px;
  border-radius: 8px;
  background: #fff;
}
/*
 * Vant 用这个伪元素画「取消」上方的灰色分隔条。面板本身就是灰的，
 * 留白改由上面的 margin-top 提供 —— 伪元素必须收掉，否则它会在白块顶部糊出一条灰带。
 */
.share :deep(.van-share-sheet__cancel)::before {
  display: none;
}

.share__feedback {
  padding: 12px 0 4px;
}
</style>
