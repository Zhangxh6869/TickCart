package com.tickcart.search.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 搜索同义词，对应 {@code search_synonym}。
 *
 * <p>同一 {@code groupId} 下的词互为同义词（对称关系）。搜索时把关键词所在组的
 * 全部词作为「展开词」一起召回，解决「苹果手机」搜不到 iPhone 这类问题。</p>
 */
@TableName("search_synonym")
public class SearchSynonym {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 同义词组 ID，同组内的词互为同义词 */
    private Integer groupId;

    /** 词条 */
    private String word;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
