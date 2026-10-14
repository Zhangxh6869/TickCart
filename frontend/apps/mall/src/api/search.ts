import request from '@/utils/request'

/**
 * 热门搜索词（公开）。
 *
 * 后端口径：真实搜索日志的词频 TOP N；日志为空时兜底「热销商品名首段」，
 * 保证任何情况下都能拿到一组「点了搜得到」的词。
 */
export function fetchHotSearches(limit = 8): Promise<string[]> {
  return request.get('/search/hot', { params: { limit } })
}

/**
 * 相关搜索词（公开）：与 `keyword` 相关的搜索词。
 *
 * 后端口径：优先取真实搜索日志里与关键词有包含关系的词，不足时用命中商品的高频词补齐。
 */
export function fetchRelatedSearches(keyword: string, limit = 8): Promise<string[]> {
  return request.get('/search/related', { params: { keyword, limit } })
}
