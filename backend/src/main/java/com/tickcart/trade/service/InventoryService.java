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
 * 无规格商品才使用 {@code product.stock}。</p>
 *
 * <p><b>★ 两套账要同步，但只有一个真账</b>：商品表的 {@code stock} 是展示用聚合值
 * （商品列表、库存告急判定、分类页告急数都读它），它<b>不参与</b>「能不能扣」的判断 ——
 * 判断只看 SKU，避免同一笔订单被扣两次。但它在扣减/回补之后必须跟着增减，
 * 否则会变成一个只有保存商品时才刷新的陈旧快照（下单后后台库存永远偏大）。</p>
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
            // ★ 同步商品汇总库存。product.stock 是商品列表与库存告急判定读的派生字段，
            //   只有「保存商品 / 单独改 SKU 库存」会重算它；不在这里同步的话，
            //   下单之后后台看到的库存会一直偏大（实测：卖 2 件，SKU 合计 198→196，
            //   product.stock 仍停在 198）。
            adjustProductStock(productId, -quantity);
            return;
        }

        // 无规格商品：product.stock 本身就是真账，只扣这一次 —— 不要再调 adjustProductStock，
        // 否则同一件货会被扣两遍
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
                // ★ 这里刻意不再调 adjustProductStock：SKU 没回补成功（行已被逻辑删除），
                //   若还去加商品汇总值，就会让汇总比真实 SKU 合计更大 —— 从「少算」变成「多算」，
                //   是更坏的错账。宁可两边都不动，并留下告警。
                log.warn("库存回补未命中 SKU（可能已被逻辑删除）：productId={}, skuId={}, quantity={}",
                        productId, skuId, quantity);
                return;
            }
            adjustProductStock(productId, quantity);
            return;
        }
        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .setSql("stock = stock + {0}", quantity));
        if (rows == 0) {
            log.warn("库存回补未命中商品：productId={}, quantity={}", productId, quantity);
        }
    }

    /**
     * 按增量调整商品汇总库存（±n），只用于「SKU 才是真账」的商品。
     *
     * <p>不加下限条件：判断能否扣减已由 SKU 那一层用 {@code stock >= n} 做过了；
     * 这里若再加条件，一旦历史数据漂移就会扣不动，反而把不一致固化下来。</p>
     */
    private void adjustProductStock(Long productId, int delta) {
        if (productId == null || delta == 0) {
            return;
        }
        String operator = delta > 0 ? "+" : "-";
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .setSql("stock = stock " + operator + " {0}", Math.abs(delta)));
    }

    private boolean hasSku(Long skuId) {
        return skuId != null && skuId > NO_SKU;
    }
}
