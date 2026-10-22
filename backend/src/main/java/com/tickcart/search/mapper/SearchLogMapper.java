package com.tickcart.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.search.dto.KeywordCount;
import com.tickcart.search.dto.KeywordStat;
import com.tickcart.search.dto.SearchTrendPoint;
import com.tickcart.search.entity.SearchLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
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

    /**
     * 批量写入搜索日志（一条 INSERT 多组 VALUES）。
     *
     * <p>不用 MyBatis-Plus 的 {@code insertBatchSomeColumn}：那需要自定义 SqlInjector，
     * 为一个批量插入扩展全局注入器不划算。这里显式写列，反而更清楚。</p>
     *
     * <p>{@code created_at} 由调用方填好 —— 自定义 SQL 不经过 {@code MetaObjectHandler}，
     * 依赖自动填充会写入 NULL。</p>
     */
    @Insert("<script>"
            + "INSERT INTO search_log (keyword, result_count, user_id, created_at) VALUES "
            + "<foreach collection='list' item='it' separator=','>"
            + "(#{it.keyword}, #{it.resultCount}, #{it.userId}, #{it.createdAt})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("list") List<SearchLog> logs);

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
     * 按自然日聚合的搜索趋势（后台迷你趋势图用）。
     *
     * <p>只返回「有日志的日期」，缺失日期由 {@code SearchLogService#trend} 补 0 ——
     * 补齐放在 Service 而非 SQL，是因为 MySQL 没有生成日期序列的简洁写法，
     * 用递归 CTE 或日历表都会把一个看板查询搞复杂。</p>
     *
     * <p>{@code day} 用 {@code DATE_FORMAT} 产出字符串，避免 DATE → LocalDate 的
     * JDBC 类型映射差异。{@code GROUP BY day} 用别名在 MySQL 下合法。</p>
     */
    @Select("""
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d')                  AS day,
                   COUNT(*)                                             AS total,
                   SUM(CASE WHEN result_count = 0 THEN 1 ELSE 0 END)    AS miss
            FROM search_log
            WHERE (#{since} IS NULL OR created_at >= #{since})
            GROUP BY day
            ORDER BY day
            """)
    List<SearchTrendPoint> trendByDay(@Param("since") LocalDateTime since);

    /**
     * 全局热门搜索词：按出现次数降序。
     *
     * <p><b>热搜词是「可点的建议」，必须点了就有结果</b>，所以这里做两道过滤
     * （前台 {@code /api/search/hot} 直接把它们渲染成 chip，点一下就是一次搜索）：</p>
     * <ul>
     *   <li>{@code CHAR_LENGTH >= 2}：单字词噪音大（与 {@link #relatedKeywords} 同一口径）；</li>
     *   <li>{@code HAVING MAX(result_count) > 0}：该词**至少成功搜到过一次**商品。
     *       否则会出现「热搜词点进去 0 结果」的死链 —— 踩过：验证脚本用 {@code _} / {@code %}
     *       当探针打接口，这些符号词被记进日志后直接挤进了热搜榜。</li>
     * </ul>
     *
     * <p>注意「搜不到的词」并没有被丢掉：它们仍完整保留在 {@code search_log} 里，
     * 供后台「搜索分析」的无结果词榜使用，只是不作为前台建议词。</p>
     */
    @Select("""
            SELECT keyword FROM search_log
            WHERE CHAR_LENGTH(keyword) >= 2
            GROUP BY keyword
            HAVING MAX(result_count) > 0
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
