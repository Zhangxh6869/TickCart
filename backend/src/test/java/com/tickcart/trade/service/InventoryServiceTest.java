package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InventoryService} 单元测试。
 *
 * <p>扣减走条件 UPDATE（{@code stock >= quantity}），影响行数为 0 即视为库存不足，
 * 借此在数据库层面避免并发超卖。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("库存服务")
class InventoryServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("有 SKU 时扣减 SKU 库存，并同步商品汇总库存")
    void deductSkuStock() {
        when(productSkuMapper.update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class))).thenReturn(1);
        when(productMapper.update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class))).thenReturn(1);

        inventoryService.deduct(1L, 5L, 2);

        verify(productSkuMapper).update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class));
        // 汇总值也要跟着减，否则商品列表/库存告急读到的还是旧数
        verify(productMapper).update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("有 SKU 但 SKU 扣减失败时，不动商品汇总库存")
    void deductSkuInsufficientKeepsProductStock() {
        when(productSkuMapper.update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.deduct(1L, 5L, 99))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
        verify(productMapper, never()).update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("无 SKU 时只扣一次商品库存（不能因为同步汇总而扣两遍）")
    void deductProductStock() {
        when(productMapper.update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class))).thenReturn(1);

        inventoryService.deduct(1L, 0L, 2);

        // verify 默认恰好一次：既断言扣了，也排除「无规格商品被扣两次」
        verify(productMapper).update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class));
        verify(productSkuMapper, never()).update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("SKU 库存不足时抛异常")
    void deductSkuInsufficient() {
        when(productSkuMapper.update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.deduct(1L, 5L, 99))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
    }

    @Test
    @DisplayName("商品库存不足时抛异常")
    void deductProductInsufficient() {
        when(productMapper.update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.deduct(1L, 0L, 99))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
    }

    @Test
    @DisplayName("回补 SKU 库存，并同步商品汇总库存")
    void restoreSkuStock() {
        when(productSkuMapper.update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class))).thenReturn(1);
        when(productMapper.update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class))).thenReturn(1);

        inventoryService.restore(1L, 5L, 2);

        verify(productSkuMapper).update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class));
        verify(productMapper).update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("回补时 SKU 已被逻辑删除：只留告警，不动商品汇总库存")
    void restoreMissingSkuKeepsProductStock() {
        when(productSkuMapper.update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class))).thenReturn(0);

        inventoryService.restore(1L, 34L, 1);

        // SKU 没补上却去加汇总值，会让汇总比真实合计更大 —— 从少算变成多算，更坏
        verify(productMapper, never()).update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("回补商品库存")
    void restoreProductStock() {
        when(productMapper.update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class))).thenReturn(1);

        inventoryService.restore(1L, 0L, 2);

        verify(productMapper).update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class));
    }
}
