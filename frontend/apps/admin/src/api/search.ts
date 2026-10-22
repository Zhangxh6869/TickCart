import request from '@/utils/request'

/** 无结果词榜单单项。 */
export interface KeywordCount {
  keyword: string
  /** 无结果搜索次数 */
  count: number
  /** 最近一次搜索时间（ISO 字符串） */
  lastSearchedAt: string
}

/** 热搜词榜单单项。 */
export interface KeywordStat {
  keyword: string
  /** 总搜索次数 */
  count: number
  /** 有结果次数 */
  hitCount: number
  /** 无结果次数 */
  missCount: number
  /** 命中率 0~1（后端计算，前端转百分比） */
  hitRate: number
  lastSearchedAt: string
}

/** 搜索分析看板数据。 */
export interface SearchStatsVO {
  /** 区间内搜索总次数 */
  totalSearches: number
  /** 区间内无结果搜索次数 */
  noResultSearches: number
  /** 区间内搜索过的关键词数（去重） */
  distinctKeywords: number
  noResultTop: KeywordCount[]
  hotTop: KeywordStat[]
}

/** 趋势中的单日数据点。 */
export interface SearchTrendPoint {
  /** 自然日 yyyy-MM-dd */
  day: string
  /** 当日搜索总次数 */
  total: number
  /** 当日无结果次数 */
  miss: number
}

/** 搜索分析看板（后台）。days<=0 表示全部时间。 */
export function fetchSearchStats(params: { days?: number; limit?: number }): Promise<SearchStatsVO> {
  return request.get('/admin/search-stats', { params })
}

/** 搜索趋势（按自然日，缺失日期后端已补 0）。days<=0 表示全部时间。 */
export function fetchSearchTrend(days: number): Promise<SearchTrendPoint[]> {
  return request.get('/admin/search-stats/trend', { params: { days } })
}

/**
 * 新增同义词：把 keyword 与 words 归入同一组，之后搜任一词都会一起召回。
 * @returns 实际新增的词条数（已存在的词会被后端跳过）
 */
export function addSearchSynonyms(keyword: string, words: string[]): Promise<number> {
  return request.post('/admin/search-synonyms', { keyword, words })
}
