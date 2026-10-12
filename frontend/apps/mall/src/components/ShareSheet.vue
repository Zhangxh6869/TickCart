<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
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

const options: ShareOption[] = [
  { key: 'wechat', name: '微信', icon: 'wechat' },
  { key: 'moments', name: '朋友圈', icon: 'wechat-moments' },
  { key: 'qq', name: 'QQ', icon: 'qq' },
  { key: 'qzone', name: 'QQ空间', icon: 'star-o', className: 'share-opt--qzone' },
  { key: 'weibo', name: '新浪微博', icon: 'weibo' },
  { key: 'link', name: '复制链接', icon: 'link', className: 'share-opt--plain' },
  { key: 'poster', name: '生成海报', icon: 'poster', className: 'share-opt--plain' },
  { key: 'report', name: '举报', icon: 'warning-o', className: 'share-opt--plain' },
  { key: 'feedback', name: '反馈与建议', icon: 'edit', className: 'share-opt--plain' },
  { key: 'more', name: '更多', icon: 'ellipsis', className: 'share-opt--plain' },
]

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
onBeforeUnmount(() => window.clearTimeout(layerTimer))

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
  <div class="share">
    <van-share-sheet
      :show="show"
      title="分享至"
      cancel-text="取消"
      :options="options"
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
/* ── 分享面板：把 Vant 的「单行横向滚动」改成参考图的「两行 5 列」 ── */
.share :deep(.van-share-sheet__options) {
  flex-wrap: wrap;
  overflow-x: visible;
  padding: 16px 0 4px;
  row-gap: 18px;
}
.share :deep(.van-share-sheet__option) {
  flex: 0 0 20%;
  width: 20%;
}
/* Vant 默认给图标左右各 16px 外边距，会把 48px 图标挤出 20% 栅格 */
.share :deep(.van-share-sheet__icon),
.share :deep(.van-share-sheet__image-icon) {
  margin: 0;
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

/* 「取消」在参考图里是带左右留白的圆角白块 */
.share :deep(.van-share-sheet__cancel) {
  margin: 0 12px 12px;
  border-radius: 8px;
}
/*
 * Vant 用这个伪元素画「取消」上方的灰色分隔条。按钮已有 margin 留白，
 * 这里把灰条铺满整宽（负外边距抵消按钮的左右留白），才不会在圆角块顶部糊出一条灰边。
 */
.share :deep(.van-share-sheet__cancel)::before {
  height: 12px;
  margin: 0 -12px;
}

.share__feedback {
  padding: 12px 0 4px;
}
</style>
