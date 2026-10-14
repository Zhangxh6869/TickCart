package com.tickcart.search.service;

import com.tickcart.search.mapper.SearchSynonymMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link SynonymService} 单测：展开、无同义词回退、空关键词、去空白。
 */
@ExtendWith(MockitoExtension.class)
class SynonymServiceTest {

    @Mock
    private SearchSynonymMapper searchSynonymMapper;

    @InjectMocks
    private SynonymService synonymService;

    @Test
    @DisplayName("命中同义词组：原词在前，组内词补齐")
    void expandsWhenSynonymExists() {
        when(searchSynonymMapper.wordsInSameGroup("苹果手机")).thenReturn(List.of("苹果手机", "iPhone"));

        assertThat(synonymService.expand("苹果手机")).containsExactly("苹果手机", "iPhone");
    }

    @Test
    @DisplayName("无同义词时只返回原关键词")
    void returnsOriginalWhenNoSynonym() {
        when(searchSynonymMapper.wordsInSameGroup("手机")).thenReturn(List.of());

        assertThat(synonymService.expand("手机")).containsExactly("手机");
    }

    @Test
    @DisplayName("空关键词返回空列表")
    void blankKeywordReturnsEmpty() {
        assertThat(synonymService.expand("   ")).isEmpty();
        assertThat(synonymService.expand(null)).isEmpty();
    }

    @Test
    @DisplayName("关键词两侧空白被裁掉")
    void trimsKeyword() {
        when(searchSynonymMapper.wordsInSameGroup("唇膏")).thenReturn(List.of("唇膏", "口红"));

        assertThat(synonymService.expand("  唇膏  ")).containsExactly("唇膏", "口红");
    }
}
