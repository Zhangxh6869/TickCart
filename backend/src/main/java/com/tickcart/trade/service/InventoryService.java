package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.ProductSkuMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 库存服务：商品库存的扣减与回补。
 *
 * <p>由 trade 模块统一声明（seckill 也复用它），避免各模块各自扣库存造成口径分裂。</p>
 *
 * <p><b>防超卖</b>：扣减使用带条件的原子 UPDATE
 * （{@code SET stock = stock - n WHERE id = ? AND stock >= n}），
 * 影响行数为 0 即代表库存不足，在数据库层面消除「先查后扣」的竞态。</p>
 *
 * <p><b>库存口径</b>：配置了规格的商品以 {@code product_sku.stock} 为准；
 * 无规格商品才使用 {@code product.stock}。商品表的 stock 是展示用聚合值，
 * 不参与实际扣减，避免同一笔订单被两套账各扣一次。</p>
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** 无规格商品的 SKU 占位值 */
    private static final long NO_SKU = 0L;

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    public InventoryService(ProductMapper productMapper, ProductSkuMapper productSkuMapper) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
    }

    /**
     * 扣减库存。
     *
     * @throws BizException 库存不足
     */
    public void deduct(Long productId, Long skuId, int quantity) {
        if (quantity <= 0) {
            throw BizException.badRequest("扣减数量必须大于 0");
        }
        if (hasSku(skuId)) {
            int rows = productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                    .eq(ProductSku::getId, skuId)
                    .ge(ProductSku::getStock, quantity)
                    .setSql("stock = stock - {0}", quantity));
            if (rows == 0) {
                throw BizException.badRequest("库存不足");
            }
            return;
        }

        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .ge(Product::getStock, quantity)
                .setSql("stock = stock - {0}", quantity));
        if (rows == 0) {
            throw BizException.badRequest("库存不足");
        }
    }

    /**
     * 回补库存（订单取消 / 超时关闭时调用）。
     *
     * <p>回补不加下限条件：库存被回补到超过初始值属于可接受的偏差，
     * 但绝不允许因回补失败导致用户下单成功后商品「凭空消失」。</p>
     *
     * <p><b>★ 命中 0 行必须留痕</b>：MyBatis-Plus 的 update 会自动补
     * {@code deleted = 0}，而订单明细的 {@code sku_id} 可能指向一条已经被逻辑删除的 SKU
     * （商品编辑重建过 SKU）。这种情况下回补不会生效，也不会抛异常 ——
     * 是「静默丢库存」。这里不改变语义（回补到已删行没有意义），
     * 但要把事件打出来，否则只能靠人肉对账发现。</p>
     */
    public void restore(Long productId, Long skuId, int quantity) {
        if (quantity <= 0) {
            return;
        }
        if (hasSku(skuId)) {
            int rows = productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                    .eq(ProductSku::getId, skuId)
                    .setSql("stock = stock + {0}", quantity));
            if (rows == 0) {
                log.warn("库存回补未命中 SKU（可能已被逻辑删除）：productId={}, skuId={}, quantity={}",
                        productId, skuId, quantity);
            }
            return;
        }
        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .setSql("stock = stock + {0}", quantity));
        if (rows == 0) {
            log.warn("库存回补未命中商品：productId={}, quantity={}", productId, quantity);
        }
    }

    private boolean hasSku(Long skuId) {
        return skuId != null && skuId > NO_SKU;
    }
}
