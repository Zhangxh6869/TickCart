package com.tickcart.cart.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.cart.dto.CartAddRequest;
import com.tickcart.cart.entity.CartItem;
import com.tickcart.cart.mapper.CartItemMapper;
import com.tickcart.cart.vo.CartSummary;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.ProductSkuMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CartService} 单元测试。
 *
 * <p>覆盖同规格合并、库存与状态校验、归属校验、失效标记与勾选合计口径。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("购物车服务")
class CartServiceTest {

    private static final Long UID = 1001L;

    @Mock
    private CartItemMapper cartItemMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;

    @InjectMocks
    private CartService cartService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CartItem.class);
    }

    private Product product(Long id, int status, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("测试商品" + id);
        p.setMainImage("/img/demo/" + id + ".png");
        p.setPrice(new BigDecimal("99.00"));
        p.setStock(stock);
        p.setStatus(status);
        return p;
    }

    private ProductSku sku(Long id, Long productId, String price, int stock) {
        ProductSku s = new ProductSku();
        s.setId(id);
        s.setProductId(productId);
        s.setSpecName("颜色");
        s.setSpecValue("黑色");
        s.setPrice(new BigDecimal(price));
        s.setStock(stock);
        return s;
    }

    private CartItem item(Long id, Long userId, Long productId, Long skuId, int quantity, int checked) {
        CartItem c = new CartItem();
        c.setId(id);
        c.setUserId(userId);
        c.setProductId(productId);
        c.setSkuId(skuId);
        c.setQuantity(quantity);
        c.setChecked(checked);
        return c;
    }

    private CartAddRequest addReq(Long productId, Long skuId, int quantity) {
        CartAddRequest r = new CartAddRequest();
        r.setProductId(productId);
        r.setSkuId(skuId);
        r.setQuantity(quantity);
        return r;
    }

    // ---------------- 加购 ----------------

    @Test
    @DisplayName("加购新商品写入购物车且默认勾选")
    void addNewItem() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(cartItemMapper.selectOne(any())).thenReturn(null);
        when(cartItemMapper.insert(any(CartItem.class))).thenReturn(1);

        cartService.add(UID, addReq(1L, 0L, 2));

        verify(cartItemMapper).insert(ArgumentMatchers.<CartItem>argThat(c ->
                c.getUserId().equals(UID)
                        && c.getProductId().equals(1L)
                        && c.getQuantity() == 2
                        && c.getChecked() == 1));
    }

    @Test
    @DisplayName("加购同商品同规格合并数量而非新增行")
    void addExistingMergesQuantity() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(cartItemMapper.selectOne(any())).thenReturn(item(10L, UID, 1L, 0L, 2, 1));
        when(cartItemMapper.updateById(any(CartItem.class))).thenReturn(1);

        cartService.add(UID, addReq(1L, 0L, 3));

        verify(cartItemMapper, never()).insert(any(CartItem.class));
        verify(cartItemMapper).updateById(ArgumentMatchers.<CartItem>argThat(c -> c.getQuantity() == 5));
    }

    @Test
    @DisplayName("加购前清理同规格的历史逻辑删除残留（避免撞唯一键）")
    void addPurgesDeletedResidue() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(7L)).thenReturn(sku(7L, 1L, "88.00", 10));
        when(cartItemMapper.selectOne(any())).thenReturn(null);
        when(cartItemMapper.insert(any(CartItem.class))).thenReturn(1);

        cartService.add(UID, addReq(1L, 7L, 1));

        verify(cartItemMapper).purgeDeleted(UID, 1L, 7L);
    }

    @Test
    @DisplayName("合并后超过单件上限时拒绝")
    void addBeyondMaxQuantity() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(cartItemMapper.selectOne(any())).thenReturn(item(10L, UID, 1L, 0L, 98, 1));

        assertThatThrownBy(() -> cartService.add(UID, addReq(1L, 0L, 5)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("单次最多购买");
    }

    @Test
    @DisplayName("商品不存在时拒绝加购")
    void addMissingProduct() {
        when(productMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> cartService.add(UID, addReq(9L, 0L, 1)))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在或已下架");
    }

    @Test
    @DisplayName("商品已下架时拒绝加购")
    void addOfflineProduct() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 0, 100));

        assertThatThrownBy(() -> cartService.add(UID, addReq(1L, 0L, 1)))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在或已下架");
    }

    @Test
    @DisplayName("SKU 库存不足时拒绝加购")
    void addBeyondSkuStock() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(5L)).thenReturn(sku(5L, 1L, "88.00", 2));

        assertThatThrownBy(() -> cartService.add(UID, addReq(1L, 5L, 3)))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
    }

    @Test
    @DisplayName("SKU 不属于该商品时拒绝")
    void addSkuOfAnotherProduct() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(5L)).thenReturn(sku(5L, 2L, "88.00", 10));

        assertThatThrownBy(() -> cartService.add(UID, addReq(1L, 5L, 1)))
                .isInstanceOf(BizException.class)
                .hasMessage("商品规格不存在");
    }

    @Test
    @DisplayName("商品配置了规格却未选规格时拒绝")
    void addWithoutSkuWhenProductHasSpec() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> cartService.add(UID, addReq(1L, 0L, 1)))
                .isInstanceOf(BizException.class)
                .hasMessage("请选择商品规格");
    }

    // ---------------- 修改数量 ----------------

    @Test
    @DisplayName("改数量超过库存时拒绝")
    void updateQuantityBeyondStock() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, UID, 1L, 0L, 1, 1));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 3));

        assertThatThrownBy(() -> cartService.updateQuantity(UID, 10L, 5))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
    }

    @Test
    @DisplayName("改数量为 0 视为删除")
    void updateQuantityZeroRemoves() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, UID, 1L, 0L, 1, 1));
        when(cartItemMapper.deleteById(10L)).thenReturn(1);

        cartService.updateQuantity(UID, 10L, 0);

        verify(cartItemMapper).deleteById(10L);
    }

    @Test
    @DisplayName("操作他人购物车项按不存在处理")
    void updateForeignItemRejected() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, 999L, 1L, 0L, 1, 1));

        assertThatThrownBy(() -> cartService.updateQuantity(UID, 10L, 2))
                .isInstanceOf(BizException.class)
                .hasMessage("购物车项不存在");
    }

    // ---------------- 切换规格 ----------------

    @Test
    @DisplayName("切换规格只改 skuId，不新增也不删除条目")
    void changeSkuUpdatesSkuId() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, UID, 1L, 5L, 2, 1));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(6L)).thenReturn(sku(6L, 1L, "99.00", 10));
        when(cartItemMapper.selectOne(any())).thenReturn(null);

        cartService.changeSku(UID, 10L, 6L);

        verify(cartItemMapper).updateById(ArgumentMatchers.<CartItem>argThat(c ->
                c.getId().equals(10L) && c.getSkuId().equals(6L) && c.getQuantity() == 2));
        // 唯一键不含 deleted 列：改 skuId 前必须先清掉目标规格的逻辑删除残留，否则撞唯一键
        verify(cartItemMapper).purgeDeleted(UID, 1L, 6L);
        verify(cartItemMapper, never()).deleteById(ArgumentMatchers.<Serializable>any());
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    @DisplayName("切换到当前已选规格时不做任何改动")
    void changeSkuSameSkuIsNoop() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, UID, 1L, 5L, 2, 1));

        cartService.changeSku(UID, 10L, 5L);

        verify(cartItemMapper, never()).updateById(any(CartItem.class));
        verify(cartItemMapper, never()).deleteById(ArgumentMatchers.<Serializable>any());
        verify(cartItemMapper, never()).purgeDeleted(any(), any(), any());
        // 幂等分支在商品/SKU 查询之前就返回了
        verify(productMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("目标规格库存不足当前数量时拒绝，保持原规格")
    void changeSkuRejectsWhenTargetStockInsufficient() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, UID, 1L, 5L, 3, 1));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(6L)).thenReturn(sku(6L, 1L, "99.00", 2));

        assertThatThrownBy(() -> cartService.changeSku(UID, 10L, 6L))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");

        verify(cartItemMapper, never()).updateById(any(CartItem.class));
    }

    @Test
    @DisplayName("目标规格已在购物车中时合并数量并删除原条目")
    void changeSkuMergesIntoExistingItem() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, UID, 1L, 5L, 2, 1));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(6L)).thenReturn(sku(6L, 1L, "99.00", 10));
        when(cartItemMapper.selectOne(any())).thenReturn(item(11L, UID, 1L, 6L, 3, 0));

        cartService.changeSku(UID, 10L, 6L);

        // 数量相加；原条目勾选，合并后取并集，避免切换后掉出结算范围
        verify(cartItemMapper).updateById(ArgumentMatchers.<CartItem>argThat(c ->
                c.getId().equals(11L) && c.getQuantity() == 5 && c.getChecked() == 1));
        verify(cartItemMapper).deleteById(10L);
    }

    @Test
    @DisplayName("合并后超过单件上限时拒绝")
    void changeSkuMergeBeyondMaxQuantity() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, UID, 1L, 5L, 98, 1));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(6L)).thenReturn(sku(6L, 1L, "99.00", 200));
        when(cartItemMapper.selectOne(any())).thenReturn(item(11L, UID, 1L, 6L, 5, 1));

        assertThatThrownBy(() -> cartService.changeSku(UID, 10L, 6L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("单次最多购买");
    }

    @Test
    @DisplayName("目标规格不属于该商品时拒绝")
    void changeSkuRejectsSkuOfAnotherProduct() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, UID, 1L, 5L, 1, 1));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(6L)).thenReturn(sku(6L, 2L, "99.00", 10));

        assertThatThrownBy(() -> cartService.changeSku(UID, 10L, 6L))
                .isInstanceOf(BizException.class)
                .hasMessage("商品规格不存在");
    }

    @Test
    @DisplayName("商品已下架时拒绝切换规格")
    void changeSkuRejectsOfflineProduct() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, UID, 1L, 5L, 1, 1));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 0, 100));

        assertThatThrownBy(() -> cartService.changeSku(UID, 10L, 6L))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在或已下架");
    }

    @Test
    @DisplayName("切换规格时越权按不存在处理")
    void changeSkuForeignItemRejected() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, 999L, 1L, 5L, 1, 1));

        assertThatThrownBy(() -> cartService.changeSku(UID, 10L, 6L))
                .isInstanceOf(BizException.class)
                .hasMessage("购物车项不存在");
    }

    @Test
    @DisplayName("目标规格为空或 0 时拒绝")
    void changeSkuRejectsBlankSkuId() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, UID, 1L, 5L, 1, 1));

        assertThatThrownBy(() -> cartService.changeSku(UID, 10L, 0L))
                .isInstanceOf(BizException.class)
                .hasMessage("商品规格不存在");
        assertThatThrownBy(() -> cartService.changeSku(UID, 10L, null))
                .isInstanceOf(BizException.class)
                .hasMessage("商品规格不存在");
    }

    // ---------------- 列表与合计 ----------------

    @Test
    @DisplayName("列表合计只统计勾选且有效的项")
    void summaryCountsCheckedValidOnly() {
        when(cartItemMapper.selectList(any())).thenReturn(java.util.List.of(
                item(1L, UID, 1L, 0L, 2, 1),
                item(2L, UID, 2L, 0L, 3, 0),
                item(3L, UID, 3L, 0L, 1, 1)
        ));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productMapper.selectById(2L)).thenReturn(product(2L, 1, 100));
        when(productMapper.selectById(3L)).thenReturn(product(3L, 0, 100));

        CartSummary summary = cartService.list(UID);

        assertThat(summary.getItems()).hasSize(3);
        assertThat(summary.getCheckedCount()).isEqualTo(2);
        assertThat(summary.getCheckedAmount()).isEqualByComparingTo("198.00");
    }

    @Test
    @DisplayName("商品下架时列表项标记为失效")
    void summaryMarksInvalidItem() {
        when(cartItemMapper.selectList(any())).thenReturn(java.util.List.of(item(1L, UID, 1L, 0L, 1, 1)));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 0, 100));

        CartSummary summary = cartService.list(UID);

        assertThat(summary.getItems().get(0).isValid()).isFalse();
        assertThat(summary.getItems().get(0).getInvalidReason()).isEqualTo("商品已下架");
        assertThat(summary.getCheckedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("商品被删除时列表项标记为失效")
    void summaryMarksDeletedProduct() {
        when(cartItemMapper.selectList(any())).thenReturn(java.util.List.of(item(1L, UID, 1L, 0L, 1, 1)));
        when(productMapper.selectById(1L)).thenReturn(null);

        CartSummary summary = cartService.list(UID);

        assertThat(summary.getItems().get(0).isValid()).isFalse();
        assertThat(summary.getItems().get(0).getInvalidReason()).isEqualTo("商品已下架");
    }

    @Test
    @DisplayName("有 SKU 时取 SKU 价格与库存")
    void summaryUsesSkuPrice() {
        when(cartItemMapper.selectList(any())).thenReturn(java.util.List.of(item(1L, UID, 1L, 5L, 2, 1)));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(5L)).thenReturn(sku(5L, 1L, "88.50", 10));

        CartSummary summary = cartService.list(UID);

        assertThat(summary.getItems().get(0).getPrice()).isEqualByComparingTo("88.50");
        assertThat(summary.getItems().get(0).getSpecValue()).isEqualTo("黑色");
        assertThat(summary.getCheckedAmount()).isEqualByComparingTo("177.00");
    }

    @Test
    @DisplayName("SKU 已被删除时列表项标记为失效")
    void summaryMarksMissingSku() {
        when(cartItemMapper.selectList(any())).thenReturn(java.util.List.of(item(1L, UID, 1L, 5L, 1, 1)));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(5L)).thenReturn(null);

        CartSummary summary = cartService.list(UID);

        assertThat(summary.getItems().get(0).isValid()).isFalse();
        assertThat(summary.getItems().get(0).getInvalidReason()).isEqualTo("商品规格已下架");
    }

    @Test
    @DisplayName("SKU 库存为 0 时列表项标记为失效")
    void summaryMarksOutOfStockSku() {
        when(cartItemMapper.selectList(any())).thenReturn(java.util.List.of(item(1L, UID, 1L, 5L, 1, 1)));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 100));
        when(productSkuMapper.selectById(5L)).thenReturn(sku(5L, 1L, "88.50", 0));

        CartSummary summary = cartService.list(UID);

        assertThat(summary.getItems().get(0).isValid()).isFalse();
        assertThat(summary.getItems().get(0).getInvalidReason()).isEqualTo("库存不足");
    }

    // ---------------- 删除与全选 ----------------

    @Test
    @DisplayName("删除他人购物车项被拒绝")
    void deleteForeignItemRejected() {
        when(cartItemMapper.selectById(10L)).thenReturn(item(10L, 999L, 1L, 0L, 1, 1));

        assertThatThrownBy(() -> cartService.delete(UID, 10L))
                .isInstanceOf(BizException.class)
                .hasMessage("购物车项不存在");
    }

    @Test
    @DisplayName("全选把该用户所有项置为勾选")
    void checkAllUpdatesEveryItem() {
        when(cartItemMapper.update(ArgumentMatchers.<CartItem>isNull(), any())).thenReturn(3);

        cartService.checkAll(UID, 1);

        verify(cartItemMapper).update(ArgumentMatchers.<CartItem>isNull(), any());
    }

    @Test
    @DisplayName("删除已勾选项用于下单后清理")
    void deleteCheckedRemovesOnlyChecked() {
        when(cartItemMapper.delete(any())).thenReturn(2);

        cartService.deleteChecked(UID);

        verify(cartItemMapper).delete(any());
    }
}
