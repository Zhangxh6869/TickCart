package com.tickcart.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 后台「搜索分析」里为一组词建立同义词关系的请求。
 *
 * <p>语义：{@code keyword} 与 {@code words} 归入同一个同义词组，之后搜其中任意一个词，
 * 都会把整组词一起召回。典型用法是把「搜不到的词」挂到已有的可搜到词上。</p>
 */
@Schema(description = "新增同义词请求")
public class SearchSynonymAddRequest {

    @Schema(description = "基准关键词（通常是搜不到的那个词）", example = "磁吸充电宝")
    @NotBlank(message = "关键词不能为空")
    @Size(max = 64, message = "关键词最长 64 个字符")
    private String keyword;

    @Schema(description = "要并入同一组的词（不含 keyword 本身）", example = "[\"充电宝\", \"移动电源\"]")
    @NotEmpty(message = "至少填写一个同义词")
    @Size(max = 8, message = "一次最多添加 8 个同义词")
    private List<@NotBlank(message = "同义词不能为空") @Size(max = 64, message = "同义词最长 64 个字符") String> words;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public List<String> getWords() {
        return words;
    }

    public void setWords(List<String> words) {
        this.words = words;
    }
}
