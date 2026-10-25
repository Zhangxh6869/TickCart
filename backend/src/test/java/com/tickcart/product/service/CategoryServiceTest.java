package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.common.cache.CacheProperties;
import com.tickcart.common.cache.CacheService;
import com.tickcart.common.config.StockProperties;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.CategorySaveRequest;
import com.tickcart.product.entity.Category;
import com.tickcart.product.mapper.CategoryMapper;
import com.tickcart.product.mapper.ProductMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CategoryService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("分类服务")
class CategoryServiceTest {

    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private CacheService cacheService;
    @Spy
    private CacheProperties cacheProperties = new CacheProperties();

    /**
     * 纯单元测试没有 Spring 容器，Lambda wrapper 解析列名要查实体 TableInfo 缓存，
     * 不预初始化就报 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Category.class);
        TableInfoHelper.initTableInfo(assistant, com.tickcart.product.entity.Product.class);
    }

    /** 用真实实现 + mock 的 CategoryMapper：总览里的告急判定要真的跑一遍 */
    private StockWarnService stockWarnService;
    private CategoryService categoryService;

    /** 让缓存在单测里透明：getList 直接执行 loader，等价于「每次都未命中」。 */
    @BeforeEach
    void stubCacheTransparent() {
        stockWarnService = new StockWarnService(categoryMapper, new StockProperties());
        categoryService = new CategoryService(categoryMapper, productMapper, cacheService,
                cacheProperties, stockWarnService);
        lenient().when(cacheService.getList(anyString(), anyLong(), eq(Category.class), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    private CategorySaveRequest req(Long parentId, String name) {
        CategorySaveRequest r = new CategorySaveRequest();
        r.setParentId(parentId);
        r.setName(name);
        return r;
    }

    @Test
    @DisplayName("新增顶级分类层级为 1")
    void createTopLevel() {
        when(categoryMapper.insert(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });

        Category created = categoryService.create(req(0L, "数码电子"));

        assertThat(created.getLevel()).isEqualTo(1);
        assertThat(created.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("新增子分类层级为父级 +1")
    void createChildLevel() {
        Category parent = new Category();
        parent.setId(1L);
        parent.setLevel(1);
        when(categoryMapper.selectById(1L)).thenReturn(parent);
        when(categoryMapper.insert(any(Category.class))).thenReturn(1);

        Category created = categoryService.create(req(1L, "手机"));

        assertThat(created.getLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("父分类不存在时拒绝")
    void createWithMissingParent() {
        when(categoryMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.create(req(99L, "手机")))
                .isInstanceOf(BizException.class)
                .hasMessage("父分类不存在");
    }

    @Test
    @DisplayName("存在子分类时禁止删除")
    void deleteWithChildren() {
        when(categoryMapper.selectById(1L)).thenReturn(new Category());
        when(categoryMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("该分类下存在子分类，无法删除");
    }

    @Test
    @DisplayName("分类下存在商品时禁止删除")
    void deleteWithProducts() {
        when(categoryMapper.selectById(1L)).thenReturn(new Category());
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("该分类下存在商品，无法删除");
    }

    @Test
    @DisplayName("查询不存在的分类抛 404")
    void getMissing() {
        when(categoryMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.getById(5L))
                .isInstanceOf(BizException.class)
                .hasMessage("分类不存在");
    }

    // -------------------------------------------------------------------------
    // 列表页窄接口
    // -------------------------------------------------------------------------

    private Category category(Long id, Integer threshold) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(0L);
        c.setName("分类" + id);
        c.setSort(1);
        c.setStatus(1);
        c.setStockWarnThreshold(threshold);
        return c;
    }

    private com.tickcart.product.entity.Product product(Long categoryId, int stock) {
        com.tickcart.product.entity.Product p = new com.tickcart.product.entity.Product();
        p.setCategoryId(categoryId);
        p.setStock(stock);
        return p;
    }

    @Test
    @DisplayName("分类总览：商品数与告急数按该分类自己的阈值算")
    void overviewCountsUsageByOwnThreshold() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1L, 10),   // 阈值 10
                category(5L, 3)));  // 阈值 3
        when(productMapper.selectList(any())).thenReturn(List.of(
                product(1L, 9), product(1L, 10),   // 9 告急，10 不告急（严格小于）
                product(5L, 2), product(5L, 3), product(5L, 100)));

        List<com.tickcart.product.vo.CategoryOverview> result = categoryService.overview();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductCount()).isEqualTo(2);
        assertThat(result.get(0).getLowStockCount()).isEqualTo(1);
        assertThat(result.get(1).getProductCount()).isEqualTo(3);
        assertThat(result.get(1).getLowStockCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("改预警线：传 null 时 SET 里仍然要包含这一列（MP 默认会剔掉 null）")
    void updateThresholdWritesNullExplicitly() {
        when(categoryMapper.selectById(5L)).thenReturn(category(5L, 8));

        categoryService.updateStockThreshold(5L, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Category>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(categoryMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("stock_warn_threshold");
        assertThat(captor.getValue().getParamNameValuePairs().values()).containsNull();
    }

    @Test
    @DisplayName("改预警线：负数被拒绝")
    void updateThresholdRejectsNegative() {
        when(categoryMapper.selectById(5L)).thenReturn(category(5L, 8));

        assertThatThrownBy(() -> categoryService.updateStockThreshold(5L, -1))
                .isInstanceOf(BizException.class)
                .hasMessage("库存预警线不能为负");
    }

    @Test
    @DisplayName("改状态：非法值被拒绝")
    void updateStatusRejectsInvalid() {
        assertThatThrownBy(() -> categoryService.updateStatus(5L, 7))
                .isInstanceOf(BizException.class)
                .hasMessage("状态值不合法");
    }

    @Test
    @DisplayName("拖拽排序：按数组顺序重写 sort，从 1 开始")
    void reorderRewritesSortByIndex() {
        when(categoryMapper.selectCount(any())).thenReturn(3L);

        categoryService.reorder(List.of(23L, 1L, 5L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Category>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(categoryMapper, times(3)).update(isNull(), captor.capture());
        List<LambdaUpdateWrapper<Category>> wrappers = captor.getAllValues();
        assertThat(wrappers.get(0).getSqlSet()).contains("sort");
        assertThat(wrappers.get(0).getParamNameValuePairs().values()).contains(1);
        assertThat(wrappers.get(1).getParamNameValuePairs().values()).contains(2);
        assertThat(wrappers.get(2).getParamNameValuePairs().values()).contains(3);
    }

    @Test
    @DisplayName("拖拽排序：只传一部分分类会被拒绝（否则未提交的 sort 会撞车）")
    void reorderRejectsPartialList() {
        when(categoryMapper.selectCount(any())).thenReturn(20L);

        assertThatThrownBy(() -> categoryService.reorder(List.of(1L, 5L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分类列表已变化");
        verify(categoryMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("拖拽排序：重复 id 被拒绝")
    void reorderRejectsDuplicates() {
        assertThatThrownBy(() -> categoryService.reorder(List.of(1L, 1L)))
                .isInstanceOf(BizException.class)
                .hasMessage("排序列表里有重复的分类");
    }
}
