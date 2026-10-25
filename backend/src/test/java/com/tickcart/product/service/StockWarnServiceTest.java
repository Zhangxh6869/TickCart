package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.common.config.StockProperties;
import com.tickcart.product.entity.Category;
import com.tickcart.product.mapper.CategoryMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link StockWarnService} 单元测试：分类树上的阈值继承。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("库存预警线解析")
class StockWarnServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    private StockProperties stockProperties;
    private StockWarnService stockWarnService;

    /**
     * 纯单元测试没有 Spring 容器，{@code LambdaQueryWrapper.select(...)} 解析列名时会查实体的
     * TableInfo 缓存，不预先初始化就报 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Category.class);
    }

    @BeforeEach
    void setUp() {
        stockProperties = new StockProperties();
        stockProperties.setWarnThreshold(10);
        stockWarnService = new StockWarnService(categoryMapper, stockProperties);
    }

    private Category category(Long id, Long parentId, Integer threshold) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(parentId);
        c.setStockWarnThreshold(threshold);
        return c;
    }

    private Map<Long, Integer> resolve(Category... categories) {
        when(categoryMapper.selectList(any())).thenReturn(List.of(categories));
        return stockWarnService.effectiveThresholds();
    }

    @Test
    @DisplayName("分类自身配了预警线就用自身的")
    void usesOwnThreshold() {
        assertThat(resolve(category(23L, 0L, 30))).containsEntry(23L, 30);
    }

    @Test
    @DisplayName("自身没配时继承父分类的")
    void inheritsFromParent() {
        Map<Long, Integer> map = resolve(
                category(1L, 0L, 20),
                category(2L, 1L, null));

        assertThat(map).containsEntry(1L, 20).containsEntry(2L, 20);
    }

    @Test
    @DisplayName("沿父链取最近一个配了值的祖先，不是最顶层的")
    void usesNearestAncestor() {
        Map<Long, Integer> map = resolve(
                category(1L, 0L, 20),
                category(2L, 1L, 5),
                category(3L, 2L, null));

        assertThat(map).containsEntry(3L, 5);
    }

    @Test
    @DisplayName("整条父链都没配时用全局配置")
    void fallsBackToGlobal() {
        Map<Long, Integer> map = resolve(
                category(1L, 0L, null),
                category(2L, 1L, null));

        assertThat(map).containsEntry(1L, 10).containsEntry(2L, 10);
    }

    @Test
    @DisplayName("父分类不存在（脏数据）时退回全局，不抛异常")
    void missingParentFallsBackToGlobal() {
        assertThat(resolve(category(2L, 99L, null))).containsEntry(2L, 10);
    }

    @Test
    @DisplayName("父链成环时不会死循环")
    void cyclicParentChainTerminates() {
        Map<Long, Integer> map = resolve(
                category(1L, 2L, null),
                category(2L, 1L, null));

        assertThat(map).containsEntry(1L, 10).containsEntry(2L, 10);
    }

    @Test
    @DisplayName("库存等于预警线不算告急，严格小于才算")
    void isLowIsStrictlyLessThan() {
        Map<Long, Integer> map = Map.of(23L, 30);

        assertThat(stockWarnService.isLow(29, 23L, map)).isTrue();
        assertThat(stockWarnService.isLow(30, 23L, map)).isFalse();
        assertThat(stockWarnService.isLow(31, 23L, map)).isFalse();
    }

    @Test
    @DisplayName("分类不在映射里（如挂在已删分类）时按全局阈值判断")
    void unknownCategoryUsesGlobal() {
        Map<Long, Integer> map = Map.of(23L, 30);

        assertThat(stockWarnService.isLow(9, 999L, map)).isTrue();
        assertThat(stockWarnService.isLow(10, 999L, map)).isFalse();
        assertThat(stockWarnService.isLow(9, null, map)).isTrue();
    }

    @Test
    @DisplayName("库存为 null 时不标记告急")
    void nullStockIsNotLow() {
        assertThat(stockWarnService.isLow(null, 1L, Map.of())).isFalse();
    }

    @Test
    @DisplayName("按阈值取值归并：组数等于取值种类数，不是分类数")
    void groupsByThresholdValue() {
        Map<Long, Integer> thresholds = new LinkedHashMap<>();
        thresholds.put(1L, 10);
        thresholds.put(6L, 10);
        thresholds.put(7L, 10);
        thresholds.put(5L, 8);
        thresholds.put(23L, 30);

        List<StockWarnService.ThresholdGroup> groups = StockWarnService.groupByThreshold(thresholds);

        assertThat(groups).hasSize(3);
        assertThat(groups).extracting(StockWarnService.ThresholdGroup::threshold)
                .containsExactly(10, 8, 30);
        assertThat(groups.get(0).categoryIds()).containsExactly(1L, 6L, 7L);
    }
}
