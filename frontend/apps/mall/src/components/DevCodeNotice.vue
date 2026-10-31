<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 开发环境验证码悬浮窗。
 *
 * <p>替代 Vant 的 toast：toast 是一闪而过的黑色胶囊，6 位数字还没看清就没了。
 * 这里做成绿色卡片，从底部缓缓升起 → 停留 → 继续上移并淡出，给足抄码时间。</p>
 *
 * <p>放在 Teleport 到 body 里，而不是留在页面内：页面切换过渡期间根节点会被加
 * transform，而 transform 会让内部的 position: fixed 从「贴视口」变成「贴页面内容」，
 * 悬浮窗就会跟着页面滚走（App.vue 里有这条实测结论）。</p>
 */
const props = withDefaults(
  defineProps<{
    /** 要展示的验证码 */
    code: string
    /** 整段动画时长（毫秒），到点自动消失 */
    duration?: number
  }>(),
  { duration: 4200 }
)

const emit = defineEmits<{ (e: 'done'): void }>()

let timer: number | undefined

onMounted(() => {
  timer = window.setTimeout(() => emit('done'), props.duration)
})

onBeforeUnmount(() => {
  window.clearTimeout(timer)
})

/** 点一下提前关掉：码已经抄走了就不用等它自己飘走 */
function dismiss() {
  window.clearTimeout(timer)
  emit('done')
}
</script>

<template>
  <Teleport to="body">
    <div class="dev-code" role="status" aria-live="polite" @click="dismiss">
      <div class="dev-code__card" :style="{ animationDuration: `${duration}ms` }">
        <span class="dev-code__label">开发环境验证码</span>
        <strong class="dev-code__value">{{ code }}</strong>
        <span class="dev-code__hint">点击关闭</span>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* 定位层只管位置，动画放卡片上：两者分开，动 transform 时不会影响 fixed 定位 */
.dev-code {
  position: fixed;
  left: 0;
  right: 0;
  /* 高屏取 18vh 更居中，矮屏（667px 一类）用 120px 兜底，避免压住「登录」按钮。
     底边还要让开 iPhone 的 home 指示条。 */
  bottom: max(120px, 18vh);
  z-index: 3000;
  display: flex;
  justify-content: center;
  padding: 0 24px calc(env(safe-area-inset-bottom, 0px));
  pointer-events: none;
}

.dev-code__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 208px;
  padding: 16px 24px 12px;
  border-radius: 16px;
  background: linear-gradient(135deg, #34c77b 0%, #12a15c 100%);
  box-shadow:
    0 12px 28px rgba(18, 161, 92, 0.32),
    0 2px 6px rgba(18, 161, 92, 0.18);
  color: #fff;
  pointer-events: auto;
  cursor: pointer;
  /* 动画时长由内联 style 注入，与 JS 的自动关闭定时器同一个值。
     缓动刻意用 linear：ease-out 会把「淡出」压到最后 0.5s 内，看着像突然消失；
     linear 才是真正的「慢慢上移、慢慢淡掉」。 */
  animation: dev-code-float 4200ms linear forwards;
}

.dev-code__label {
  font-size: 12px;
  letter-spacing: 0.04em;
  opacity: 0.9;
}

.dev-code__value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 27px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0.18em;
  /* 字距会让最后一位右侧多出一段空白，往左补回来才是视觉居中 */
  text-indent: 0.18em;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.12);
}

.dev-code__hint {
  font-size: 11px;
  opacity: 0.75;
}

/* 升起 → 停留 → 继续上移并淡出。
   节奏按百分比切：前 9%（约 380ms）入场，到 68%（约 2.9s）几乎不动 —— 这段是留给人抄码的；
   之后 1.3s 里匀速上移 64px 并淡到 0，就是「慢慢飘走」。 */
@keyframes dev-code-float {
  0% {
    opacity: 0;
    transform: translate3d(0, 26px, 0) scale(0.94);
  }
  9% {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
  68% {
    opacity: 1;
    transform: translate3d(0, -8px, 0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translate3d(0, -64px, 0) scale(0.98);
  }
}

/* 矮屏（iPhone SE 一类 667px）：登录表单几乎占满一屏，卡片必须收小并压低，
   否则会盖住「登录」按钮。实测 375x667 下 top 从 440 降到约 496，让开按钮底边 482。 */
@media (max-height: 720px) {
  .dev-code {
    bottom: 80px;
  }

  .dev-code__card {
    gap: 4px;
    padding: 12px 20px 10px;
  }

  .dev-code__value {
    font-size: 24px;
  }
}

/* 尊重系统「减少动态效果」：不位移，只做淡入淡出 */
@media (prefers-reduced-motion: reduce) {
  .dev-code__card {
    animation: dev-code-fade 4200ms ease forwards;
  }

  @keyframes dev-code-fade {
    0%,
    100% {
      opacity: 0;
    }
    10%,
    85% {
      opacity: 1;
    }
  }
}
</style>
