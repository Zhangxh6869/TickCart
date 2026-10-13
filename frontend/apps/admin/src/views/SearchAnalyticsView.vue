<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  fetchSearchStats,
  type SearchStatsVO,
} from '@/api/search'

const days = ref(7)
const loading = ref(false)
const stats = ref<SearchStatsVO | null>(null)

async function load() {
  loading.value = true
  try {
    stats.value = await fetchSearchStats({ days: days.value, limit: 20 })
  } finally {
    loading.value = false
  }
}

onMounted(load)

function fmtRate(r: number): string {
  return (r * 100).toFixed(1) + '%'
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  // 2026-10-13T12:00:00 -> 2026-10-13 12:00:00
  return t.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <div class="sa">
    <div class="sa__toolbar">
      <span class="sa__label">时间范围</span>
      <el-radio-group v-model="days" @change="load">
        <el-radio-button :value="7">最近 7 天</el-radio-button>
        <el-radio-button :value="30">最近 30 天</el-radio-button>
        <el-radio-button :value="0">全部</el-radio-button>
      </el-radio-group>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="16" class="sa__cards">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="sa__metric">{{ stats?.totalSearches ?? 0 }}</div>
          <div class="sa__metric-label">搜索总次数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="sa__metric sa__metric--warn">{{ stats?.noResultSearches ?? 0 }}</div>
          <div class="sa__metric-label">无结果搜索次数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="sa__metric">{{ stats?.distinctKeywords ?? 0 }}</div>
          <div class="sa__metric-label">搜索关键词数</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="sa__tables">
      <el-col :span="12">
        <el-card shadow="never" header="无结果词 TOP 20">
          <el-table :data="stats?.noResultTop ?? []" v-loading="loading" empty-text="暂无无结果搜索词">
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="keyword" label="关键词" min-width="120" />
            <el-table-column prop="count" label="搜索次数" width="110" sortable />
            <el-table-column label="最近搜索" width="180">
              <template #default="{ row }">{{ fmtTime(row.lastSearchedAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" header="热搜词 TOP 20">
          <el-table :data="stats?.hotTop ?? []" v-loading="loading" empty-text="暂无搜索记录">
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="keyword" label="关键词" min-width="120" />
            <el-table-column prop="count" label="总次数" width="90" sortable />
            <el-table-column label="命中率" width="100">
              <template #default="{ row }">{{ fmtRate(row.hitRate) }}</template>
            </el-table-column>
            <el-table-column prop="missCount" label="无结果" width="90" />
            <el-table-column label="最近搜索" width="180">
              <template #default="{ row }">{{ fmtTime(row.lastSearchedAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.sa {
  padding: 4px;
}
.sa__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.sa__label {
  color: #606266;
  font-size: 14px;
}
.sa__cards {
  margin-bottom: 16px;
}
.sa__metric {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}
.sa__metric--warn {
  color: #e6a23c;
}
.sa__metric-label {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}
</style>
