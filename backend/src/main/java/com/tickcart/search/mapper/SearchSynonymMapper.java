package com.tickcart.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.search.entity.SearchSynonym;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 搜索同义词 Mapper。
 */
@Mapper
public interface SearchSynonymMapper extends BaseMapper<SearchSynonym> {

    /**
     * 取与 {@code word} 同组的所有词（含自身）。整词相等匹配、大小写不敏感。
     *
     * <p>用子查询先定位 group_id，再取该组全部词 —— 一步完成「找组 + 取组」。</p>
     */
    @Select("""
            SELECT word FROM search_synonym
            WHERE group_id IN (SELECT group_id FROM search_synonym WHERE word = #{word})
            ORDER BY id
            """)
    List<String> wordsInSameGroup(@Param("word") String word);

    /**
     * 查词条所属的同义词组 ID，不存在返回 {@code null}。
     *
     * <p>用 {@code LIMIT 1} 而非聚合：表上 {@code word} 无唯一约束，
     * 但业务上同一词只应属于一个组，取一条即可，避免脏数据导致查询报错。</p>
     */
    @Select("SELECT group_id FROM search_synonym WHERE word = #{word} LIMIT 1")
    Integer findGroupId(@Param("word") String word);

    /** 当前最大组 ID，空表返回 0（新建组时 +1 使用）。 */
    @Select("SELECT COALESCE(MAX(group_id), 0) FROM search_synonym")
    int maxGroupId();

    /** 词条是否已存在于表中（任一组合）。 */
    @Select("SELECT COUNT(*) FROM search_synonym WHERE word = #{word}")
    int countByWord(@Param("word") String word);
}
