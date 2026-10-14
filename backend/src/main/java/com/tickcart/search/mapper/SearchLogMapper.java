package com.tickcart.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.search.dto.KeywordCount;
import com.tickcart.search.dto.KeywordStat;
import com.tickcart.search.entity.SearchLog;
import org.apache.ibatis.annotations.Delete;
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

    /**
     * 全局热门搜索词：按出现次数降序。
     */
    @Select("""
            SELECT keyword FROM search_log
            GROUP BY keyword
            ORDER BY COUNT(*) DESC
            LIMIT #{limit}
            """)
    List<String> hotKeywords(@Param("limit") int limit);

    /**
     * 与关键词相关的搜索词：另一条搜索词与本关键词存在「包含」关系（双向）。
     *
     * <p>用 {@code LOCATE(keyword, kw) > 0} 判断「本关键词包含历史词」——避免对
     * keyword 列做动态转义；用 {@code keyword LIKE %kw%}（kw 已转义）判断反向包含。
     * 单字历史词噪音大，用 {@code CHAR_LENGTH >= 2} 过滤。</p>
     */
    @Select("""
            SELECT keyword FROM search_log
            WHERE keyword <> #{kw}
              AND CHAR_LENGTH(keyword) >= 2
              AND (keyword LIKE CONCAT('%', #{like}, '%') ESCAPE '!'
                   OR LOCATE(keyword, #{kw}) > 0)
            GROUP BY keyword
            ORDER BY COUNT(*) DESC
            LIMIT #{limit}
            """)
    List<String> relatedKeywords(@Param("kw") String kw,
                                 @Param("like") String like,
                                 @Param("limit") int limit);

    /**
     * 删除早于 {@code cutoff} 的搜索日志（保留策略，见 {@code SearchLogCleanupTask}）。
     *
     * @return 实际删除行数
     */
    @Delete("DELETE FROM search_log WHERE created_at < #{cutoff}")
    int deleteBefore(@Param("cutoff") LocalDateTime cutoff);
}
