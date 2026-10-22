<script setup lang="ts">
import { computed } from 'vue'

/**
 * 极简迷你折线（sparkline）。
 *
 * 用途：在不引入图表库的前提下表达「最近 N 天的走势」。
 * 刻意不画坐标轴、网格和刻度 —— 它只回答「在涨还是在跌」，
 * 具体数值由旁边的文字给出，避免在 100×30 的空间里塞信息。
 */
const props = withDefaults(
  defineProps<{
    /** 数据点，按时间升序 */
    data: number[]
    color?: string
    width?: number
    height?: number
    /** 是否填充折线下方面积 */
    area?: boolean
  }>(),
  {
    color: '#f56c6c',
    width: 96,
    height: 26,
    area: true,
  },
)

const geom = computed(() => {
  const d = props.data
  const w = props.width
  const h = props.height
  if (!d.length) {
    return { line: '', fill: '' }
  }
  const min = Math.min(...d)
  const max = Math.max(...d)
  const span = max - min || 1
  // 单点时画一条水平线，避免只有 "M" 指令导致路径不可见
  const px = (i: number) => (d.length === 1 ? w / 2 : 1 + ((w - 2) * i) / (d.length - 1))
  const py = (v: number) => h - 2 - ((v - min) / span) * (h - 4)

  const line =
    d.length === 1
      ? `M1,${py(d[0]).toFixed(1)} L${w - 1},${py(d[0]).toFixed(1)}`
      : d.map((v, i) => `${i ? 'L' : 'M'}${px(i).toFixed(1)},${py(v).toFixed(1)}`).join(' ')
  const fill = `${line} L${px(d.length - 1).toFixed(1)},${h} L${px(0).toFixed(1)},${h} Z`
  return { line, fill }
})
</script>

<template>
  <svg
    class="spark"
    :width="width"
    :height="height"
    :viewBox="`0 0 ${width} ${height}`"
    aria-hidden="true"
  >
    <path v-if="area" :d="geom.fill" :fill="color" opacity="0.1" />
    <path
      :d="geom.line"
      fill="none"
      :stroke="color"
      stroke-width="1.6"
      stroke-linecap="round"
      stroke-linejoin="round"
      opacity="0.9"
    />
  </svg>
</template>

<style scoped>
.spark {
  display: block;
  flex: 0 0 auto;
}
</style>
