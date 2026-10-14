package com.tickcart.search.util;

import com.tickcart.product.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SearchTermExtractor} 单测：抽词、过滤规格串、排除关键词、按频次排序。
 */
class SearchTermExtractorTest {

    private static Product product(String name, String subtitle) {
        Product p = new Product();
        p.setName(name);
        p.setSubtitle(subtitle);
        return p;
    }

    @Test
    @DisplayName("抽出中文候选词，滤掉 12+256G / 168g 这类不含中文的规格串")
    void extractsCjkTermsAndDropsSpecs() {
        List<Product> products = List.of(
                product("5G 旗舰智能手机 12+256G 曜夜黑", "骁龙旗舰芯片，超清三摄"),
                product("5G 轻薄智能手机 8+128G 星空灰", "轻至 168g，长续航"));

        List<String> terms = SearchTermExtractor.extract(products, "手机", 20);

        assertThat(terms).contains("旗舰智能手机", "轻薄智能手机", "骁龙旗舰芯片", "超清三摄", "长续航");
        assertThat(terms).noneMatch(t -> t.contains("256G") || t.contains("128G") || t.equals("168g"));
    }

    @Test
    @DisplayName("关键词本身不作为相关词再推给用户")
    void excludesQueryItself() {
        List<Product> products = List.of(product("车载手机支架", "稳固防抖"));
        List<String> terms = SearchTermExtractor.extract(products, "车载手机支架", 20);

        assertThat(terms).doesNotContain("车载手机支架");
        assertThat(terms).contains("稳固防抖");
    }

    @Test
    @DisplayName("出现次数多的词排在前面")
    void ordersByFrequency() {
        List<Product> products = List.of(
                product("手机 快充", "快充"),
                product("手机 数据线", "快充"));

        List<String> terms = SearchTermExtractor.extract(products, "x", 20);

        assertThat(terms.get(0)).isEqualTo("快充");
    }

    @Test
    @DisplayName("空输入 / limit<=0 返回空列表")
    void emptyInput() {
        assertThat(SearchTermExtractor.extract(null, "手机", 8)).isEmpty();
        assertThat(SearchTermExtractor.extract(List.of(), "手机", 8)).isEmpty();
        assertThat(SearchTermExtractor.extract(List.of(product("手机壳", null)), "手机", 0)).isEmpty();
    }
}
