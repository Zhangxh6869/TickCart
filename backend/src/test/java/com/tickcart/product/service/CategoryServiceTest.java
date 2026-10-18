package com.tickcart.product.service;

import com.tickcart.common.cache.CacheProperties;
import com.tickcart.common.cache.CacheService;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.CategorySaveRequest;
import com.tickcart.product.entity.Category;
import com.tickcart.product.mapper.CategoryMapper;
import com.tickcart.product.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    @InjectMocks
    private CategoryService categoryService;

    /** 让缓存在单测里透明：getList 直接执行 loader，等价于「每次都未命中」。 */
    @BeforeEach
    void stubCacheTransparent() {
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
}
