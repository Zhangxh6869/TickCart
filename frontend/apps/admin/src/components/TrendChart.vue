<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 轻量折线图（纯 SVG，不引图表库）。
 *
 * <p>与 `SparkLine` 的分工：SparkLine 是「迷你走势条」，不画坐标轴，只回答在涨还是在跌；
 * 本组件是「正经折线图」—— 有 X 轴日期、Y 轴刻度、网格线、数据点和悬浮提示，
 * 用于需要读出「哪天多少」的看板趋势。</p>
 *
 * <p>宽度用 ResizeObserver 实测，坐标全部按真实像素算，不用 viewBox 缩放，
 * 避免文字被拉伸变形。</p>
 */
export interface TrendPoint {
  day: string
  value: number
}

const props = withDefaults(
  defineProps<{
    data: TrendPoint[]
    color?: string
    height?: number
    /** 是否填充折线下方面积 */
    area?: boolean
    /** 悬浮提示里的数值格式（Y 轴用内置紧凑格式） */
    format?: (value: number) => string
  }>(),
  {
    color: '#409eff',
    height: 200,
    area: true,
    format: (value: number) => String(value),
  },
)

/** 内边距：左侧留给 Y 轴刻度，底部留给日期 */
const PAD = { top: 14, right: 14, bottom: 26, left: 48 }

const wrap = ref<HTMLElement | null>(null)
const width = ref(560)
const hoverIndex = ref(-1)

let observer: ResizeObserver | null = null

onMounted(() => {
  if (!wrap.value) return
  observer = new ResizeObserver((entries) => {
    const w = entries[0]?.contentRect.width ?? 0
    if (w > 0) width.value = Math.floor(w)
  })
  observer.observe(wrap.value)
})

onBeforeUnmount(() => observer?.disconnect())

const innerW = computed(() => Math.max(10, width.value - PAD.left - PAD.right))
const innerH = computed(() => Math.max(10, props.height - PAD.top - PAD.bottom))

const hasData = computed(() => props.data.length > 0)

const maxValue = computed(() => props.data.reduce((m, p) => Math.max(m, p.value), 0))

/** 取一个「整齐」的刻度步长（1/2/5 × 10^n），避免出现 137、274 这种刻度 */
function niceStep(raw: number): number {
  if (raw <= 0) return 1
  const exp = Math.floor(Math.log10(raw))
  const base = Math.pow(10, exp)
  const n = raw / base
  const mult = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10
  return mult * base
}

const yStep = computed(() => niceStep(maxValue.value / 4))
const yMax = computed(() => Math.max(yStep.value, Math.ceil(maxValue.value / yStep.value) * yStep.value))

/** Y 轴刻度值（含 0 与最大值） */
const yTicks = computed(() => {
  const ticks: number[] = []
  for (let v = 0; v <= yMax.value + 1e-6; v += yStep.value) {
    ticks.push(Math.round(v * 100) / 100)
  }
  return ticks
})

/** 坐标点：X 按索引均分（没有数据时给空数组） */
const points = computed(() => {
  const n = props.data.length
  if (n === 0) return []
  const stepX = n === 1 ? 0 : innerW.value / (n - 1)
  return props.data.map((p, i) => ({
    day: p.day,
    value: p.value,
    x: PAD.left + (n === 1 ? innerW.value / 2 : i * stepX),
    y: PAD.top + innerH.value * (1 - (yMax.value > 0 ? p.value / yMax.value : 0)),
  }))
})

const linePoints = computed(() => points.value.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' '))

const areaPath = computed(() => {
  const pts = points.value
  if (pts.length === 0) return ''
  const bottom = PAD.top + innerH.value
  const head = `M${pts[0].x.toFixed(1)},${bottom}`
  const body = pts.map((p) => `L${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
  const tail = `L${pts[pts.length - 1].x.toFixed(1)},${bottom} Z`
  return `${head} ${body} ${tail}`
})

/** X 轴最多显示 7 个日期，多了就隔几个取一个（首尾必显示） */
const xLabels = computed(() => {
  const pts = points.value
  const n = pts.length
  if (n === 0) return []
  const maxLabels = 7
  const step = Math.max(1, Math.ceil(n / maxLabels))
  return pts.filter((_, i) => i % step === 0 || i === n - 1)
})

/** Y 轴刻度文案：过万用「万」，过千用「k」，避免刻度挤成一团 */
function axisText(v: number): string {
  if (v >= 10000) {
    const w = v / 10000
    return `${Number.isInteger(w) ? w : w.toFixed(1)}万`
  }
  if (v >= 1000) {
    const k = v / 1000
    return `${Number.isInteger(k) ? k : k.toFixed(1)}k`
  }
  return String(v)
}

/** 只显示「月-日」，省宽度 */
function dayText(day: string): string {
  return day.length >= 10 ? day.slice(5) : day
}

const hovered = computed(() => (hoverIndex.value >= 0 ? points.value[hoverIndex.value] : null))

function onMove(event: MouseEvent) {
  const pts = points.value
  if (pts.length === 0) return
  const rect = (event.currentTarget as SVGRectElement).getBoundingClientRect()
  const x = event.clientX - rect.left
  // 找最近的索引，比按区间判定更跟手
  let nearest = 0
  let min = Infinity
  pts.forEach((p, i) => {
    const d = Math.abs(p.x - x)
    if (d < min) {
      min = d
      nearest = i
    }
  })
  hoverIndex.value = nearest
}

const tipStyle = computed(() => {
  const p = hovered.value
  if (!p) return {}
  // 贴右边界时向左翻转，避免提示被裁掉
  const flip = p.x > width.value - 110
  return {
    left: `${flip ? p.x - 12 : p.x + 12}px`,
    top: `${Math.max(4, p.y - 12)}px`,
    transform: flip ? 'translateX(-100%)' : 'none',
  }
})
</script>

<template>
  <div ref="wrap" class="trend" :style="{ height: `${height}px` }">
    <svg v-if="hasData" :width="width" :height="height" class="trend__svg">
      <!-- 网格线 + Y 轴刻度 -->
      <g>
        <template v-for="t in yTicks" :key="`y-${t}`">
          <line
            :x1="PAD.left"
            :x2="width - PAD.right"
            :y1="PAD.top + innerH * (1 - (yMax > 0 ? t / yMax : 0))"
            :y2="PAD.top + innerH * (1 - (yMax > 0 ? t / yMax : 0))"
            stroke="#f0f2f5"
            stroke-width="1"
          />
          <text
            :x="PAD.left - 8"
            :y="PAD.top + innerH * (1 - (yMax > 0 ? t / yMax : 0))"
            text-anchor="end"
            dominant-baseline="central"
            class="trend__axis"
          >
            {{ axisText(t) }}
          </text>
        </template>
      </g>

      <!-- 面积 + 折线 -->
      <path v-if="area" :d="areaPath" :fill="color" opacity="0.1" />
      <polyline
        :points="linePoints"
        fill="none"
        :stroke="color"
        stroke-width="2"
        stroke-linejoin="round"
        stroke-linecap="round"
      />

      <!-- 数据点 -->
      <circle
        v-for="(p, i) in points"
        :key="`p-${i}`"
        :cx="p.x"
        :cy="p.y"
        r="3"
        :fill="color"
        stroke="#fff"
        stroke-width="1.5"
      />

      <!-- X 轴日期 -->
      <text
        v-for="p in xLabels"
        :key="`x-${p.day}`"
        :x="p.x"
        :y="height - 8"
        text-anchor="middle"
        class="trend__axis"
      >
        {{ dayText(p.day) }}
      </text>

      <!-- 悬浮竖线 + 高亮点 -->
      <g v-if="hovered">
        <line
          :x1="hovered.x"
          :x2="hovered.x"
          :y1="PAD.top"
          :y2="PAD.top + innerH"
          stroke="#dcdfe6"
          stroke-width="1"
          stroke-dasharray="3 3"
        />
        <circle :cx="hovered.x" :cy="hovered.y" r="4.5" :fill="color" stroke="#fff" stroke-width="2" />
      </g>

      <!-- 透明覆盖层：接鼠标事件 -->
      <rect
        :x="PAD.left"
        :y="PAD.top"
        :width="innerW"
        :height="innerH"
        fill="transparent"
        @mousemove="onMove"
        @mouseleave="hoverIndex = -1"
      />
    </svg>

    <div v-else class="trend__empty">暂无数据</div>

    <div v-if="hovered" class="trend__tip" :style="tipStyle">
      <b>{{ hovered.day }}</b>
      <span>{{ format(hovered.value) }}</span>
    </div>
  </div>
</template>

<style scoped>
.trend {
  position: relative;
  width: 100%;
}
.trend__svg {
  display: block;
}
.trend__axis {
  font-size: 11px;
  fill: #a8abb2;
  font-variant-numeric: tabular-nums;
}
.trend__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 13px;
  color: #c0c4cc;
}
.trend__tip {
  position: absolute;
  pointer-events: none;
  background: rgba(48, 49, 51, 0.9);
  color: #fff;
  border-radius: 4px;
  padding: 5px 9px;
  font-size: 12px;
  line-height: 1.5;
  white-space: nowrap;
  z-index: 2;
}
.trend__tip b {
  display: block;
  font-weight: 500;
  color: #e4e7ed;
}
.trend__tip span {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
</style>
