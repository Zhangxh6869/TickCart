package com.tickcart.search.service;

import com.tickcart.common.exception.BizException;
import com.tickcart.search.entity.SearchSynonym;
import com.tickcart.search.mapper.SearchSynonymMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SynonymService} 单测：展开、无同义词回退、空关键词、去空白；
 * 以及后台「+ 同义词」的建组 / 追加 / 去重 / 跳过已存在词。
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

    // ---------------- 后台「+ 同义词」写入 ----------------
    // 核心不变式：同一个词在表里只能属于一个组。因此「已存在的词」一律跳过，
    // 不做跨组迁移 —— 迁移会悄悄改变已有搜索的召回范围，属于运营不可预期的副作用。

    @Test
    @DisplayName("addWords：关键词不在表中时新建组，关键词与同义词一起入库")
    void createsNewGroup() {
        when(searchSynonymMapper.findGroupId("磁吸充电宝")).thenReturn(null);
        when(searchSynonymMapper.maxGroupId()).thenReturn(3);
        when(searchSynonymMapper.countByWord(anyString())).thenReturn(0);

        int added = synonymService.addWords("磁吸充电宝", List.of("充电宝", "移动电源"));

        assertThat(added).isEqualTo(3);

        ArgumentCaptor<SearchSynonym> captor = ArgumentCaptor.forClass(SearchSynonym.class);
        verify(searchSynonymMapper, times(3)).insert((SearchSynonym) captor.capture());
        List<SearchSynonym> inserted = captor.getAllValues();
        assertThat(inserted).extracting(SearchSynonym::getWord)
                .containsExactly("磁吸充电宝", "充电宝", "移动电源");
        assertThat(inserted).extracting(SearchSynonym::getGroupId)
                .containsOnly(4);
    }

    @Test
    @DisplayName("addWords：关键词已在某组时只追加新词，不重复插入关键词")
    void appendsToExistingGroup() {
        when(searchSynonymMapper.findGroupId("充电宝")).thenReturn(7);
        when(searchSynonymMapper.countByWord("移动电源")).thenReturn(0);

        int added = synonymService.addWords("充电宝", List.of("移动电源", "充电宝"));

        // words 里的「充电宝」与 keyword 相同 → 去重时已被剔除，实际只新增 1 个
        assertThat(added).isEqualTo(1);

        ArgumentCaptor<SearchSynonym> captor = ArgumentCaptor.forClass(SearchSynonym.class);
        verify(searchSynonymMapper).insert((SearchSynonym) captor.capture());
        assertThat(captor.getValue().getWord()).isEqualTo("移动电源");
        assertThat(captor.getValue().getGroupId()).isEqualTo(7);
    }

    @Test
    @DisplayName("addWords：同义词已存在于表里时跳过，不插入")
    void skipsExistingWords() {
        when(searchSynonymMapper.findGroupId("充电宝")).thenReturn(7);
        when(searchSynonymMapper.countByWord("移动电源")).thenReturn(1);

        int added = synonymService.addWords("充电宝", List.of("移动电源"));

        assertThat(added).isZero();
        verify(searchSynonymMapper, never()).insert(any(SearchSynonym.class));
    }

    @Test
    @DisplayName("addWords：关键词为空时抛业务异常")
    void rejectsBlankKeyword() {
        assertThatThrownBy(() -> synonymService.addWords("  ", List.of("充电宝")))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("addWords：同义词全为空串时抛业务异常，不建组")
    void rejectsBlankWords() {
        assertThatThrownBy(() -> synonymService.addWords("磁吸充电宝", List.of("  ", "")))
                .isInstanceOf(BizException.class);
        verify(searchSynonymMapper, never()).insert(any(SearchSynonym.class));
    }
}
