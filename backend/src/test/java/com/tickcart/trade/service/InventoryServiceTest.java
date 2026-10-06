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
    @DisplayName("有 SKU 时扣减 SKU 库存")
    void deductSkuStock() {
        when(productSkuMapper.update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class))).thenReturn(1);

        inventoryService.deduct(1L, 5L, 2);

        verify(productSkuMapper).update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class));
        verify(productMapper, never()).update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("无 SKU 时扣减商品库存")
    void deductProductStock() {
        when(productMapper.update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class))).thenReturn(1);

        inventoryService.deduct(1L, 0L, 2);

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
    @DisplayName("回补 SKU 库存")
    void restoreSkuStock() {
        when(productSkuMapper.update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class))).thenReturn(1);

        inventoryService.restore(1L, 5L, 2);

        verify(productSkuMapper).update(ArgumentMatchers.<ProductSku>isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("回补商品库存")
    void restoreProductStock() {
        when(productMapper.update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class))).thenReturn(1);

        inventoryService.restore(1L, 0L, 2);

        verify(productMapper).update(ArgumentMatchers.<Product>isNull(), any(Wrapper.class));
    }
}
