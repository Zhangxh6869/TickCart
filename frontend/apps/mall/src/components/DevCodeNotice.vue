<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'

/**
 * 开发环境验证码提示条。
 *
 * <p>替代 Vant 的 toast：toast 一闪而过的黑色胶囊，6 位数字还没看清就没了。
 * 这里做成屏幕顶部的浅绿色条，一条稍深的色带在 3 秒内从左扫到右 —— 色带既是装饰
 * 也是倒计时，扫满即消失，用户能直观看到「还剩多久」。</p>
 *
 * <p>放在 Teleport 到 body 里，而不是留在页面内：页面切换过渡期间根节点会被加
 * transform，而 transform 会让内部的 position: fixed 从「贴视口」变成「贴页面内容」，
 * 提示条就会跟着页面滚走（App.vue 里有这条实测结论）。</p>
 */
const FADE_MS = 300

const props = withDefaults(
  defineProps<{
    /** 要展示的验证码 */
    code: string
    /** 整段时长（毫秒）= 色带扫完的时间 + 淡出时间，到点自动移除 */
    duration?: number
  }>(),
  { duration: 3300 }
)

const emit = defineEmits<{ (e: 'done'): void }>()

/** 色带扫满所需时间：总时长减掉最后的淡出，扫满与淡出无缝衔接 */
const fillMs = computed(() => Math.max(0, props.duration - FADE_MS))
/** 两个动画的时间参数用 CSS 变量下发，避免 keyframes 里写死百分比、改时长就错位 */
const animStyle = computed(() => ({
  '--fill': `${fillMs.value}ms`,
  '--fade-delay': `${fillMs.value}ms`,
}))

let timer: number | undefined

onMounted(() => {
  timer = window.setTimeout(() => emit('done'), props.duration)
})

onBeforeUnmount(() => {
  window.clearTimeout(timer)
})

/** 点一下提前关掉：码已经抄走了就不用等它扫完 */
function dismiss() {
  window.clearTimeout(timer)
  emit('done')
}
</script>

<template>
  <Teleport to="body">
    <div class="dev-code" role="status" aria-live="polite" @click="dismiss">
      <div class="dev-code__card" :style="animStyle">
        <!-- 从左扫到右的深色带：宽度动画即倒计时 -->
        <span class="dev-code__progress" aria-hidden="true"></span>
        <svg class="dev-code__icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
          <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2" />
        </svg>
        <span class="dev-code__text">
          验证码<strong class="dev-code__value">{{ code }}</strong>
        </span>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* 定位层只管位置，动画放卡片上：两者分开，动 transform 时不会影响 fixed 定位。
   贴在屏幕上方，并让开刘海/home 指示条那套安全区。 */
.dev-code {
  position: fixed;
  left: 0;
  right: 0;
  top: calc(env(safe-area-inset-top, 0px) + 12px);
  z-index: 3000;
  padding: 0 16px;
  pointer-events: none;
}

.dev-code__card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
  height: 52px;
  padding: 0 18px;
  border-radius: 16px;
  background: #e2f6ec;
  box-shadow: 0 6px 18px rgba(18, 161, 92, 0.12);
  color: #2b9d66;
  pointer-events: auto;
  cursor: pointer;
  /* 入场：从上方 6px 落下并淡入（220ms）；
     出场：色带扫满时开始淡出。两个动画分开写，出场用 forwards 而不是 both，
     否则它在 delay 期间会用 0% 帧把入场动画盖掉（opacity 一直 0）。 */
  animation:
    dev-code-in 220ms ease-out both,
    dev-code-out 300ms linear var(--fade-delay, 3000ms) forwards;
}

/* 深一点的色带：绝对定位铺在内容下方，只改 width 不改 left，
   保证「从左往右长」而不是整块平移 */
.dev-code__progress {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 0;
  background: rgba(18, 161, 92, 0.13);
  animation: dev-code-fill var(--fill, 3000ms) linear forwards;
}

.dev-code__icon,
.dev-code__text {
  position: relative;
  /* 压在色带之上，否则扫过来时文字会被半透明色块盖一层 */
  z-index: 1;
}

.dev-code__icon {
  flex-shrink: 0;
  color: #34c77b;
}

.dev-code__text {
  font-size: 14px;
  line-height: 1;
  letter-spacing: 0.02em;
}

.dev-code__value {
  margin-left: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

@keyframes dev-code-fill {
  from {
    width: 0;
  }
  to {
    width: 100%;
  }
}

@keyframes dev-code-in {
  from {
    opacity: 0;
    transform: translate3d(0, -6px, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes dev-code-out {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}

/* 尊重系统「减少动态效果」：去掉下落位移，色带照常扫（它承载的是倒计时信息） */
@media (prefers-reduced-motion: reduce) {
  .dev-code__card {
    animation:
      dev-code-out 300ms linear var(--fade-delay, 3000ms) forwards;
  }
}
</style>
