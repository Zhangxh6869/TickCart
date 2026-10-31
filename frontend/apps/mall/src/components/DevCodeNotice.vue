<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 开发环境验证码悬浮窗。
 *
 * <p>替代 Vant 的 toast：toast 是一闪而过的黑色胶囊，6 位数字还没看清就没了。
 * 这里做成绿色卡片，在屏幕 1/4 高处淡入 → 定点停留 3 秒 → 淡出，给足抄码时间。</p>
 *
 * <p>放在 Teleport 到 body 里，而不是留在页面内：页面切换过渡期间根节点会被加
 * transform，而 transform 会让内部的 position: fixed 从「贴视口」变成「贴页面内容」，
 * 悬浮窗就会跟着页面滚走（App.vue 里有这条实测结论）。</p>
 */
const props = withDefaults(
  defineProps<{
    /** 要展示的验证码 */
    code: string
    /** 整段动画时长（毫秒），到点自动消失。与 CSS keyframes 的百分比按同一时长换算 */
    duration?: number
  }>(),
  { duration: 3600 }
)

const emit = defineEmits<{ (e: 'done'): void }>()

let timer: number | undefined

onMounted(() => {
  timer = window.setTimeout(() => emit('done'), props.duration)
})

onBeforeUnmount(() => {
  window.clearTimeout(timer)
})

/** 点一下提前关掉：码已经抄走了就不用等它自己淡走 */
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
/* 定位层只管位置，动画放卡片上：两者分开，动 transform 时不会影响 fixed 定位。
   top: 25vh = 屏幕 1/4 处（从顶部量），悬浮窗从这一行淡入。 */
.dev-code {
  position: fixed;
  left: 0;
  right: 0;
  top: 25vh;
  z-index: 3000;
  display: flex;
  justify-content: center;
  padding: 0 24px;
  pointer-events: none;
}

.dev-code__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 172px;
  padding: 12px 20px 10px;
  border-radius: 14px;
  background: linear-gradient(135deg, #34c77b 0%, #12a15c 100%);
  box-shadow:
    0 10px 24px rgba(18, 161, 92, 0.28),
    0 2px 5px rgba(18, 161, 92, 0.16);
  color: #fff;
  pointer-events: auto;
  cursor: pointer;
  /* 缓动刻意用 linear：ease-out 会把「淡出」压到最后 0.5s 内，看着像突然消失；
     linear 才是匀速淡掉。时长由内联 style 注入，与 JS 自动关闭的定时器同一个值。 */
  animation: dev-code-float 3600ms linear forwards;
}

.dev-code__label {
  font-size: 11px;
  letter-spacing: 0.04em;
  opacity: 0.9;
}

.dev-code__value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0.16em;
  /* 字距会让最后一位右侧多出一段空白，往左补回来才是视觉居中 */
  text-indent: 0.16em;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.12);
}

.dev-code__hint {
  font-size: 10px;
  opacity: 0.72;
}

/* 淡入 → 定点不动 → 淡出。
   百分比按 3600ms 换算：7% ≈ 250ms 入场完成，83% ≈ 3000ms 开始淡出，
   中间这段（250ms ~ 3000ms）位置与透明度都锁死，就是「定点展示 3 秒」。
   最后 600ms 只上移 12px，让消失有点方向感，又不至于变成「飘走」。 */
@keyframes dev-code-float {
  0% {
    opacity: 0;
    transform: translate3d(0, 10px, 0) scale(0.94);
  }
  7% {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
  83% {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translate3d(0, -12px, 0) scale(0.98);
  }
}

/* 尊重系统「减少动态效果」：不位移，只做淡入淡出 */
@media (prefers-reduced-motion: reduce) {
  .dev-code__card {
    animation: dev-code-fade 3600ms ease forwards;
  }

  @keyframes dev-code-fade {
    0%,
    100% {
      opacity: 0;
    }
    7%,
    83% {
      opacity: 1;
    }
  }
}
</style>
