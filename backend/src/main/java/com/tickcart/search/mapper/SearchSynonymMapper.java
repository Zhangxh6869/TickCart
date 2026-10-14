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
}
