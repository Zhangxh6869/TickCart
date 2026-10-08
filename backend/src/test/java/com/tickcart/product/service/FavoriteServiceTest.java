package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.common.api.ResultCode;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.FavoriteQuery;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.UserFavorite;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.UserFavoriteMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FavoriteService} 单元测试。
 *
 * <p>重点：幂等（重复收藏/取消不报错）、并发唯一键冲突被吞掉、
 * 以及收藏列表按收藏顺序返回并过滤已删除商品。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("收藏服务")
class FavoriteServiceTest {

    private static final Long UID = 1001L;

    @Mock
    private UserFavoriteMapper favoriteMapper;
    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private FavoriteService favoriteService;

    /**
     * 纯单元测试没有 Spring 容器，而 {@code LambdaQueryWrapper} 解析列名依赖实体的
     * TableInfo 缓存，需先手动初始化，否则报 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserFavorite.class);
    }

    private Product product(Long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setCategoryId(1L);
        p.setName(name);
        p.setSubtitle("副标题" + id);
        p.setMainImage("/img/" + id + ".jpg");
        p.setPrice(new BigDecimal("99.00"));
        p.setStock(10);
        p.setSales(3);
        p.setStatus(1);
        p.setIsSeckill(0);
        return p;
    }

    private UserFavorite favorite(Long productId) {
        UserFavorite f = new UserFavorite();
        f.setId(productId * 10);
        f.setUserId(UID);
        f.setProductId(productId);
        return f;
    }

    // ---------------- 收藏 ----------------

    @Test
    @DisplayName("收藏：写入当前用户与商品ID")
    void addInsertsFavorite() {
        when(productMapper.selectById(10L)).thenReturn(product(10L, "商品10"));
        when(favoriteMapper.selectCount(any())).thenReturn(0L);
        when(favoriteMapper.insert(any(UserFavorite.class))).thenReturn(1);

        favoriteService.add(UID, 10L);

        ArgumentCaptor<UserFavorite> captor = ArgumentCaptor.forClass(UserFavorite.class);
        verify(favoriteMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(UID);
        assertThat(captor.getValue().getProductId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("收藏：商品不存在时拒绝")
    void addRejectsMissingProduct() {
        when(productMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> favoriteService.add(UID, 999L));

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.NOT_FOUND);
        verify(favoriteMapper, never()).insert(any(UserFavorite.class));
    }

    @Test
    @DisplayName("收藏幂等：已收藏时不重复写入")
    void addIsIdempotent() {
        when(productMapper.selectById(10L)).thenReturn(product(10L, "商品10"));
        when(favoriteMapper.selectCount(any())).thenReturn(1L);

        favoriteService.add(UID, 10L);

        verify(favoriteMapper, never()).insert(any(UserFavorite.class));
    }

    @Test
    @DisplayName("收藏并发：唯一键冲突被吞掉，不抛异常")
    void addSwallowsDuplicateKey() {
        when(productMapper.selectById(10L)).thenReturn(product(10L, "商品10"));
        when(favoriteMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_user_product"))
                .when(favoriteMapper).insert(any(UserFavorite.class));

        favoriteService.add(UID, 10L);

        verify(favoriteMapper).insert(any(UserFavorite.class));
    }

    // ---------------- 取消收藏 ----------------

    @Test
    @DisplayName("取消收藏：按用户与商品物理删除")
    void removeDeletesRow() {
        favoriteService.remove(UID, 10L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<UserFavorite>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(favoriteMapper).delete(captor.capture());
        // 条件值在 SQL 片段生成时才写入参数表，先实体化再断言
        assertThat(captor.getValue().getSqlSegment()).contains("user_id", "product_id");
        assertThat(captor.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(UID, 10L);
    }

    @Test
    @DisplayName("取消收藏幂等：没收藏过也视为成功")
    void removeIsIdempotent() {
        when(favoriteMapper.delete(any())).thenReturn(0);

        favoriteService.remove(UID, 10L);

        verify(favoriteMapper).delete(any());
    }

    // ---------------- 是否已收藏 ----------------

    @Test
    @DisplayName("是否已收藏：有记录为 true")
    void existsReturnsTrue() {
        when(favoriteMapper.selectCount(any())).thenReturn(1L);
        assertThat(favoriteService.exists(UID, 10L)).isTrue();
    }

    @Test
    @DisplayName("是否已收藏：无记录为 false")
    void existsReturnsFalse() {
        when(favoriteMapper.selectCount(any())).thenReturn(0L);
        assertThat(favoriteService.exists(UID, 10L)).isFalse();
    }

    // ---------------- 收藏列表 ----------------

    @Test
    @DisplayName("收藏列表：按收藏顺序返回商品，并过滤已删除商品")
    void pageMapsProductsInFavoriteOrder() {
        // 收藏顺序：11 在前、10 在后；其中 12 的商品已被删除
        when(favoriteMapper.selectPage(ArgumentMatchers.<IPage<UserFavorite>>any(), any()))
                .thenAnswer(inv -> {
                    IPage<UserFavorite> p = inv.getArgument(0);
                    p.setRecords(List.of(favorite(11L), favorite(12L), favorite(10L)));
                    p.setTotal(3);
                    return p;
                });
        when(productMapper.selectBatchIds(any()))
                .thenReturn(List.of(product(10L, "商品10"), product(11L, "商品11")));

        var result = favoriteService.page(UID, new FavoriteQuery());

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(11L);
        assertThat(result.getRecords().get(1).getId()).isEqualTo(10L);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("商品11");
        // total 仍按收藏记录数统计，避免翻页错乱
        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    @DisplayName("收藏列表：没有收藏时直接返回空，不查商品")
    void pageWithNoFavorites() {
        when(favoriteMapper.selectPage(ArgumentMatchers.<IPage<UserFavorite>>any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = favoriteService.page(UID, new FavoriteQuery());

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(productMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("收藏列表：分页参数被规范化")
    void pageClampsPaging() {
        when(favoriteMapper.selectPage(ArgumentMatchers.<IPage<UserFavorite>>any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        FavoriteQuery query = new FavoriteQuery();
        query.setPage(0);
        query.setSize(999);
        favoriteService.page(UID, query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<IPage<UserFavorite>> captor = ArgumentCaptor.forClass(IPage.class);
        verify(favoriteMapper).selectPage(captor.capture(), any());
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(50);
    }
}
