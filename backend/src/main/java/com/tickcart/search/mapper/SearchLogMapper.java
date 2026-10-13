package com.tickcart.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.search.dto.KeywordCount;
import com.tickcart.search.dto.KeywordStat;
import com.tickcart.search.entity.SearchLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 搜索日志 Mapper。
 *
 * <p>除 MyBatis-Plus 自带的单条写入，另提供两类聚合查询，供后台「搜索分析」看板使用：
 * ① 无结果词 TOP N（命中数为 0 的关键词按次数降序）；② 热搜词 TOP N（全部关键词按次数降序，
 * 并拆分有结果/无结果次数以计算命中率）。</p>
 *
 * <p>{@code since} 为 NULL 时表示「全部时间」，SQL 中统一用 {@code (since IS NULL OR created_at >= since)}
 * 兼容，避免调用方反复拼条件。</p>
 */
@Mapper
public interface SearchLogMapper extends BaseMapper<SearchLog> {

    @Select("""
            SELECT keyword,
                   COUNT(*)        AS count,
                   MAX(created_at) AS lastSearchedAt
            FROM search_log
            WHERE result_count = 0
              AND (#{since} IS NULL OR created_at >= #{since})
            GROUP BY keyword
            ORDER BY count DESC
            LIMIT #{limit}
            """)
    List<KeywordCount> noResultTopN(@Param("limit") int limit, @Param("since") LocalDateTime since);

    @Select("""
            SELECT keyword,
                   COUNT(*)                                           AS count,
                   SUM(CASE WHEN result_count > 0 THEN 1 ELSE 0 END) AS hitCount,
                   SUM(CASE WHEN result_count = 0 THEN 1 ELSE 0 END) AS missCount,
                   MAX(created_at)                                    AS lastSearchedAt
            FROM search_log
            WHERE (#{since} IS NULL OR created_at >= #{since})
            GROUP BY keyword
            ORDER BY count DESC
            LIMIT #{limit}
            """)
    List<KeywordStat> hotTopN(@Param("limit") int limit, @Param("since") LocalDateTime since);

    @Select("""
            SELECT COUNT(*) FROM search_log
            WHERE (#{since} IS NULL OR created_at >= #{since})
            """)
    long totalSearches(@Param("since") LocalDateTime since);

    @Select("""
            SELECT COUNT(*) FROM search_log
            WHERE result_count = 0
              AND (#{since} IS NULL OR created_at >= #{since})
            """)
    long noResultSearches(@Param("since") LocalDateTime since);

    @Select("""
            SELECT COUNT(DISTINCT keyword) FROM search_log
            WHERE (#{since} IS NULL OR created_at >= #{since})
            """)
    int distinctKeywords(@Param("since") LocalDateTime since);
}
