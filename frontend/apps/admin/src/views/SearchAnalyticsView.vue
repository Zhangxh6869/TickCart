<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  addSearchSynonyms,
  fetchSearchStats,
  fetchSearchTrend,
  type SearchStatsVO,
  type SearchTrendPoint,
} from '@/api/search'
import SparkLine from '@/components/SparkLine.vue'

const router = useRouter()

/** 时间范围：0 = 全部时间 */
const days = ref(7)
const loading = ref(false)
const stats = ref<SearchStatsVO | null>(null)
const trend = ref<SearchTrendPoint[]>([])
/** 无结果词最小次数过滤：0 = 不过滤 */
const minCount = ref(0)

// ---------------- 派生指标 ----------------
const totalSearches = computed(() => stats.value?.totalSearches ?? 0)
const noResultSearches = computed(() => stats.value?.noResultSearches ?? 0)

/** 无结果率：看板里最该被看到的数字，所以放在顶部大字 */
const noResultRate = computed(() =>
  totalSearches.value > 0 ? noResultSearches.value / totalSearches.value : 0,
)
const hitRate = computed(() => 1 - noResultRate.value)

/** 无结果率越高越红：>=20% 危险、>=10% 关注、其余正常 */
const heroLevel = computed(() => {
  if (noResultRate.value >= 0.2) return 'danger'
  if (noResultRate.value >= 0.1) return 'warn'
  return 'ok'
})

const rangeLabel = computed(() => (days.value > 0 ? `近 ${days.value} 天` : '全部时间'))

/** 迷你趋势画的是「每日无结果率」，与顶部大字口径一致 */
const trendRates = computed(() =>
  trend.value.map((p) => (p.total > 0 ? p.miss / p.total : 0)),
)

const trendRange = computed(() => {
  const list = trend.value
  if (list.length < 2) return ''
  const rateOf = (p: SearchTrendPoint) => (p.total > 0 ? p.miss / p.total : 0)
  return `${pct(rateOf(list[0]))} → ${pct(rateOf(list[list.length - 1]))}`
})

/** 趋势线颜色跟随健康度（SVG 的 stroke 属性不解析 CSS 变量，只能传具体色值） */
const sparkColor = computed(() => {
  if (heroLevel.value === 'danger') return '#f56c6c'
  if (heroLevel.value === 'warn') return '#e6a23c'
  return '#67c23a'
})

/** 首尾差值：<0 在变好、>0 在变坏、=0 持平（持平不标色，避免 0%→0% 被误标成红色） */
const trendDelta = computed(() => {
  const list = trend.value
  if (list.length < 2) return 0
  const rateOf = (p: SearchTrendPoint) => (p.total > 0 ? p.miss / p.total : 0)
  return rateOf(list[list.length - 1]) - rateOf(list[0])
})

/** 结论：把 TOP3 无结果词直接点出来，省得运营自己从榜里找 */
const verdict = computed(() => {
  const top = (stats.value?.noResultTop ?? []).slice(0, 3).map((it) => it.keyword)
  if (totalSearches.value === 0) {
    return `${rangeLabel.value}还没有搜索记录。`
  }
  if (!top.length) {
    return `${rangeLabel.value}共 ${num(totalSearches.value)} 次搜索，没有搜不到的词 —— 命中情况良好。`
  }
  return `${rangeLabel.value} ${num(totalSearches.value)} 次搜索里有 ${num(noResultSearches.value)} 次什么都没搜到，集中在「${top.join(' / ')}」`
})

const filteredNoResult = computed(() => {
  const list = stats.value?.noResultTop ?? []
  return minCount.value > 0 ? list.filter((it) => it.count >= minCount.value) : list
})

/**
 * 热搜词只保留「至少命中过一次」的词。
 *
 * 后端 hotTopN 是全部关键词按次数排序，会混进 0 命中的词 ——
 * 而那些词已经在左边「搜不到的词」里了，两边重复展示既浪费版面，
 * 也让人误以为「热搜词质量很差」。
 */
const hotList = computed(() => (stats.value?.hotTop ?? []).filter((it) => it.hitCount > 0))

const noResultMax = computed(() =>
  Math.max(1, ...(stats.value?.noResultTop ?? []).map((it) => it.count)),
)
const hotMax = computed(() => Math.max(1, ...hotList.value.map((it) => it.count)))

const noResultEmptyText = computed(() => {
  if (!(stats.value?.noResultTop ?? []).length) {
    return `${rangeLabel.value}没有搜不到的词，搜索命中情况良好`
  }
  return `没有出现 ${minCount.value} 次及以上的词，长尾噪音居多`
})

// ---------------- 数据加载 ----------------
async function load() {
  loading.value = true
  try {
    const [s, t] = await Promise.all([
      fetchSearchStats({ days: days.value, limit: 20 }),
      fetchSearchTrend(days.value),
    ])
    stats.value = s
    trend.value = t
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ---------------- 同义词弹窗 ----------------
const dialogVisible = ref(false)
const synonymTarget = ref('')
const synonymInput = ref('')
const submitting = ref(false)

function openSynonym(keyword: string) {
  synonymTarget.value = keyword
  synonymInput.value = ''
  dialogVisible.value = true
}

async function submitSynonym() {
  // 逗号（中英文）、空格、顿号都当分隔符，运营怎么敲都行
  const words = synonymInput.value
    .split(/[,，、\s]+/)
    .map((w) => w.trim())
    .filter(Boolean)
  if (!words.length) {
    ElMessage.warning('请至少填写一个同义词')
    return
  }
  submitting.value = true
  try {
    const added = await addSearchSynonyms(synonymTarget.value, words)
    ElMessage.success(
      added > 0 ? `已添加 ${added} 个同义词，下次搜这些词会一起召回` : '这些词已存在，无需重复添加',
    )
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

/** 去商品管理建商品，把关键词带过去预填筛选 */
function goCreateProduct(keyword: string) {
  router.push({ path: '/products', query: { keyword } })
}

// ---------------- 格式化 ----------------
function pct(v: number): string {
  return `${(v * 100).toFixed(1)}%`
}

function num(n: number): string {
  return n.toLocaleString('zh-CN')
}

/** 榜单里只显示月日时分，年份对「最近 N 天」没有信息量 */
function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.replace('T', ' ').slice(5, 16)
}

function barWidth(count: number, max: number): string {
  return `${Math.max(2, (count / max) * 100)}%`
}

/** 命中率越低越红，运营扫一眼就能挑出有问题的词 */
function hitTagType(rate: number): 'danger' | 'warning' | 'success' {
  if (rate < 0.8) return 'danger'
  if (rate < 0.9) return 'warning'
  return 'success'
}
</script>

<template>
  <div class="sa" v-loading="loading">
    <!-- ============ 健康条：一眼看到「多差」+「在变好还是变坏」 ============ -->
    <section class="sa-hero" :class="`sa-hero--${heroLevel}`">
      <div class="sa-hero__main">
        <div class="sa-hero__num">{{ pct(noResultRate) }}</div>
        <div class="sa-hero__label">无结果率 · {{ rangeLabel }}</div>
      </div>

      <div class="sa-hero__mid">
        <p class="sa-hero__verdict">{{ verdict }}</p>
        <div class="sa-hero__split">
          <span>有结果 {{ pct(hitRate) }}</span>
          <div class="sa-hero__bar">
            <i class="sa-hero__bar-hit" :style="{ width: `${hitRate * 100}%` }" />
            <i class="sa-hero__bar-miss" :style="{ width: `${noResultRate * 100}%` }" />
          </div>
          <span>无结果 {{ pct(noResultRate) }}</span>

          <div v-if="trendRates.length > 1" class="sa-hero__trend">
            <SparkLine :data="trendRates" :width="96" :height="26" :color="sparkColor" />
            <span class="sa-hero__trend-txt">
              {{ rangeLabel }}
              <b :class="trendDelta < 0 ? 'is-good' : trendDelta > 0 ? 'is-bad' : ''">
                {{ trendRange }}
              </b>
            </span>
          </div>
        </div>
      </div>

      <div class="sa-hero__right">
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
          <el-radio-button :value="0">全部</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <div class="sa-grid">
      <!-- ============ 搜不到的词：可操作清单 ============ -->
      <el-card shadow="never" class="sa-card">
        <template #header>
          <div class="sa-card__head">
            <div class="sa-card__title">
              <i class="sa-card__mark sa-card__mark--danger" />
              搜不到的词
              <span class="sa-card__sub">按次数排序 · 前 20</span>
            </div>
            <el-radio-group v-model="minCount" size="small">
              <el-radio-button :value="0">全部</el-radio-button>
              <el-radio-button :value="3">≥3 次</el-radio-button>
              <el-radio-button :value="10">≥10 次</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <div v-if="filteredNoResult.length" class="sa-list">
          <div v-for="(it, i) in filteredNoResult" :key="it.keyword" class="sa-row">
            <span class="sa-row__rank" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
            <span class="sa-row__kw" :title="it.keyword">{{ it.keyword }}</span>
            <div class="sa-row__bar">
              <i :style="{ width: barWidth(it.count, noResultMax) }" />
            </div>
            <span class="sa-row__cnt">{{ it.count }}</span>
            <span class="sa-row__time">{{ fmtTime(it.lastSearchedAt) }}</span>
            <div class="sa-row__acts">
              <el-button link type="primary" size="small" @click="openSynonym(it.keyword)">
                + 同义词
              </el-button>
              <el-button size="small" type="primary" plain @click="goCreateProduct(it.keyword)">
                建商品
              </el-button>
            </div>
          </div>
        </div>
        <el-empty v-else :description="noResultEmptyText" :image-size="72" />
      </el-card>

      <!-- ============ 热搜词：条形长度 = 搜索量 ============ -->
      <el-card shadow="never" class="sa-card">
        <template #header>
          <div class="sa-card__head">
            <div class="sa-card__title">
              <i class="sa-card__mark sa-card__mark--success" />
              热搜词
              <span class="sa-card__sub">条长 = 搜索量 · 橙红 = 命中率偏低</span>
            </div>
          </div>
        </template>

        <div v-if="hotList.length" class="sa-hot">
          <div v-for="it in hotList" :key="it.keyword" class="sa-hot__row">
            <span class="sa-hot__kw" :title="it.keyword">{{ it.keyword }}</span>
            <div class="sa-hot__bar">
              <i
                :class="{ 'is-low': it.hitRate < 0.9 }"
                :style="{ width: barWidth(it.count, hotMax) }"
              />
            </div>
            <span class="sa-hot__val">{{ it.count }}</span>
            <el-tag size="small" :type="hitTagType(it.hitRate)" effect="light">
              {{ pct(it.hitRate) }}
            </el-tag>
          </div>
        </div>
        <el-empty v-else description="还没有命中过商品的搜索词" :image-size="72" />
      </el-card>
    </div>

    <!-- ============ 同义词弹窗：把「搜不到」变成「下次搜得到」 ============ -->
    <el-dialog v-model="dialogVisible" title="添加同义词" width="460px">
      <el-form label-width="88px" @submit.prevent>
        <el-form-item label="关键词">
          <el-tag type="danger" effect="plain" size="large">{{ synonymTarget }}</el-tag>
        </el-form-item>
        <el-form-item label="同义词">
          <el-input
            v-model="synonymInput"
            type="textarea"
            :rows="2"
            placeholder="如：充电宝、移动电源（逗号或空格分隔）"
          />
          <div class="sa-dialog__tip">
            添加后，搜「{{ synonymTarget }}」或这些同义词会召回同一批商品。
            已存在的词会被自动跳过。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitSynonym">确认添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sa {
  --sa-primary: var(--el-color-primary, #409eff);
  --sa-danger: var(--el-color-danger, #f56c6c);
  --sa-warn: var(--el-color-warning, #e6a23c);
  --sa-success: var(--el-color-success, #67c23a);
  --sa-text-2: var(--el-text-color-regular, #606266);
  --sa-text-3: var(--el-text-color-secondary, #909399);
  --sa-text-4: var(--el-text-color-placeholder, #c0c4cc);
  --sa-border: var(--el-border-color-lighter, #ebeef5);

  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ---------------- 健康条 ---------------- */
.sa-hero {
  position: relative;
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 20px 24px;
  background: linear-gradient(135deg, #fff 0%, #f7fbff 100%);
  border: 1px solid #d9ecff;
  border-radius: 8px;
  overflow: hidden;
}
/* 左侧色条随无结果率变色，滚动时也能看到「红还是绿」 */
.sa-hero::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: linear-gradient(180deg, var(--sa-danger), #ffb3b3);
}
.sa-hero--warn::before {
  background: linear-gradient(180deg, var(--sa-warn), #f8d9a0);
}
.sa-hero--ok::before {
  background: linear-gradient(180deg, var(--sa-success), #b3e19d);
}
.sa-hero--warn {
  border-color: #faecd8;
  background: linear-gradient(135deg, #fff 0%, #fffcf5 100%);
}
.sa-hero--ok {
  border-color: #e1f3d8;
  background: linear-gradient(135deg, #fff 0%, #f8fdf5 100%);
}

.sa-hero__main {
  flex: 0 0 auto;
}
.sa-hero__num {
  font-size: 42px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -1px;
  color: var(--sa-danger);
  font-variant-numeric: tabular-nums;
}
.sa-hero--warn .sa-hero__num {
  color: var(--sa-warn);
}
.sa-hero--ok .sa-hero__num {
  color: var(--sa-success);
}
.sa-hero__label {
  margin-top: 8px;
  font-size: 13px;
  color: var(--sa-text-3);
}

.sa-hero__mid {
  flex: 1;
  min-width: 0;
}
.sa-hero__verdict {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-primary, #303133);
}
.sa-hero__split {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--sa-text-3);
  white-space: nowrap;
}
.sa-hero__bar {
  display: flex;
  flex: 0 0 150px;
  height: 8px;
  border-radius: 5px;
  overflow: hidden;
  background: var(--el-fill-color-light, #f5f7fa);
}
.sa-hero__bar-hit {
  display: block;
  height: 100%;
  background: var(--sa-primary);
}
.sa-hero__bar-miss {
  display: block;
  height: 100%;
  background: var(--sa-danger);
}
/* 迷你趋势：左边当期快照，右边走势 */
.sa-hero__trend {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-left: 6px;
  padding-left: 12px;
  border-left: 1px solid var(--el-border-color, #dcdfe6);
}
.sa-hero__trend-txt {
  font-size: 11px;
  color: var(--sa-text-3);
}
.sa-hero__trend-txt b {
  font-variant-numeric: tabular-nums;
}
.sa-hero__trend-txt b.is-good {
  color: var(--sa-success);
}
.sa-hero__trend-txt b.is-bad {
  color: var(--sa-danger);
}

.sa-hero__right {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

/* ---------------- 两栏 ---------------- */
.sa-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 16px;
  align-items: stretch;
}
.sa-card {
  border-radius: 8px;
}
.sa-card :deep(.el-card__header) {
  padding: 14px 18px;
}
.sa-card :deep(.el-card__body) {
  padding: 6px 0;
}
/* 空状态给足呼吸空间，两栏等高时不会贴在卡片顶部 */
.sa-card :deep(.el-empty) {
  padding: 56px 0;
}
.sa-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.sa-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.sa-card__mark {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--sa-primary);
}
.sa-card__mark--danger {
  background: var(--sa-danger);
}
.sa-card__mark--success {
  background: var(--sa-success);
}
.sa-card__sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--sa-text-4);
}

/* ---------------- 无结果词清单 ---------------- */
.sa-list {
  padding-bottom: 4px;
}
.sa-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--el-fill-color-lighter, #fafafa);
  transition: background 0.15s;
}
.sa-row:last-child {
  border-bottom: 0;
}
.sa-row:hover {
  background: #fafcff;
}
.sa-row__rank {
  flex: 0 0 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--sa-text-3);
  background: var(--el-fill-color-light, #f4f4f5);
  font-variant-numeric: tabular-nums;
}
.sa-row__rank.is-top {
  color: var(--sa-danger);
  background: #fef0f0;
}
.sa-row__kw {
  flex: 0 0 104px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sa-row__bar {
  flex: 1;
  min-width: 40px;
  height: 6px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--el-fill-color-light, #f0f2f5);
}
.sa-row__bar i {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #ffb3b3, var(--sa-danger));
}
.sa-row__cnt {
  flex: 0 0 46px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: var(--sa-danger);
  font-variant-numeric: tabular-nums;
}
.sa-row__time {
  flex: 0 0 92px;
  text-align: right;
  font-size: 12px;
  color: var(--sa-text-4);
  font-variant-numeric: tabular-nums;
}
.sa-row__acts {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
}

/* ---------------- 热搜词 ---------------- */
.sa-hot {
  padding: 8px 18px 12px;
}
.sa-hot__row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
}
.sa-hot__kw {
  flex: 0 0 92px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sa-hot__bar {
  flex: 1;
  min-width: 40px;
  height: 8px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--el-fill-color-lighter, #f5f7fa);
}
.sa-hot__bar i {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #a0cfff, var(--sa-primary));
}
.sa-hot__bar i.is-low {
  background: linear-gradient(90deg, #f8d9a0, var(--sa-warn));
}
.sa-hot__val {
  flex: 0 0 42px;
  text-align: right;
  font-size: 12px;
  color: var(--sa-text-2);
  font-variant-numeric: tabular-nums;
}

/* ---------------- 弹窗 ---------------- */
.sa-dialog__tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--sa-text-3);
}

@media (max-width: 1280px) {
  .sa-grid {
    grid-template-columns: 1fr;
  }
}
</style>
