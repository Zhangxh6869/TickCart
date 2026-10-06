package com.tickcart.cart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tickcart.cart.dto.CartAddRequest;
import com.tickcart.cart.entity.CartItem;
import com.tickcart.cart.mapper.CartItemMapper;
import com.tickcart.cart.vo.CartItemVO;
import com.tickcart.cart.vo.CartSummary;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 购物车服务。
 *
 * <p>规则要点：</p>
 * <ul>
 *   <li><b>合并</b>：同商品同规格累加数量（依赖唯一键 {@code uk_user_product_sku}）</li>
 *   <li><b>实时性</b>：价格与库存取商品/SKU 当前值，加购与改数量时校验库存</li>
 *   <li><b>失效</b>：商品下架、规格被删、库存为 0 时列表项标记 {@code valid=false} 并给出原因，
 *       但不自动删除，便于用户感知；失效项不计入合计</li>
 *   <li><b>归属</b>：所有写操作校验 {@code user_id}，越权按不存在处理</li>
 * </ul>
 */
@Service
public class CartService {

    /** 单个购物车项的数量上限 */
    private static final int MAX_QUANTITY_PER_ITEM = 99;

    /** 无规格商品的 SKU 占位值 */
    private static final long NO_SKU = 0L;

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    public CartService(CartItemMapper cartItemMapper,
                       ProductMapper productMapper,
                       ProductSkuMapper productSkuMapper) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
    }

    /** 购物车列表 + 勾选合计。 */
    public CartSummary list(Long userId) {
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getId));

        List<CartItemVO> vos = new ArrayList<>(items.size());
        int checkedCount = 0;
        BigDecimal checkedAmount = BigDecimal.ZERO;

        for (CartItem item : items) {
            CartItemVO vo = buildItemVo(item);
            vos.add(vo);
            if (vo.isValid() && Integer.valueOf(1).equals(item.getChecked())) {
                checkedCount += item.getQuantity();
                checkedAmount = checkedAmount.add(vo.getSubtotal());
            }
        }

        CartSummary summary = new CartSummary();
        summary.setItems(vos);
        summary.setCheckedCount(checkedCount);
        summary.setCheckedAmount(checkedAmount);
        summary.setTotalCount(items.size());
        return summary;
    }

    /** 购物车项条数（tabbar 角标用）。 */
    public int count(Long userId) {
        Long count = cartItemMapper.selectCount(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
        return count == null ? 0 : count.intValue();
    }

    /** 加入购物车：同商品同规格合并数量，并自动勾选。 */
    @Transactional
    public void add(Long userId, CartAddRequest req) {
        Product product = productMapper.selectById(req.getProductId());
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw BizException.badRequest("商品不存在或已下架");
        }

        long skuId = req.getSkuId() == null ? NO_SKU : req.getSkuId();
        ProductSku sku = null;
        if (skuId > NO_SKU) {
            sku = productSkuMapper.selectById(skuId);
            if (sku == null || !product.getId().equals(sku.getProductId())) {
                throw BizException.badRequest("商品规格不存在");
            }
        } else if (hasRealSpec(product.getId())) {
            // 商品配置了规格却不传 skuId 时，若放行会走商品级库存校验，
            // 导致 SKU 库存与商品库存各算一套，下单扣减错乱
            throw BizException.badRequest("请选择商品规格");
        }

        int quantity = req.getQuantity() == null ? 1 : req.getQuantity();
        if (quantity <= 0) {
            throw BizException.badRequest("购买数量必须大于 0");
        }

        CartItem existing = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, product.getId())
                .eq(CartItem::getSkuId, skuId));

        int target = (existing == null ? 0 : existing.getQuantity()) + quantity;
        if (target > MAX_QUANTITY_PER_ITEM) {
            throw BizException.badRequest("单次最多购买 " + MAX_QUANTITY_PER_ITEM + " 件");
        }

        int available = sku != null ? nullToZero(sku.getStock()) : nullToZero(product.getStock());
        if (available < target) {
            throw BizException.badRequest("库存不足");
        }

        if (existing == null) {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(product.getId());
            item.setSkuId(skuId);
            item.setQuantity(quantity);
            item.setChecked(1);
            cartItemMapper.insert(item);
        } else {
            existing.setQuantity(target);
            // 重新加购时自动勾选，符合「刚加进来就要买」的直觉
            existing.setChecked(1);
            cartItemMapper.updateById(existing);
        }
    }

    /** 修改数量；传 0 表示删除该项。 */
    @Transactional
    public void updateQuantity(Long userId, Long id, int quantity) {
        CartItem item = getOwned(userId, id);
        if (quantity <= 0) {
            cartItemMapper.deleteById(id);
            return;
        }
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw BizException.badRequest("单次最多购买 " + MAX_QUANTITY_PER_ITEM + " 件");
        }
        int available = availableStock(item.getProductId(), item.getSkuId());
        if (available < quantity) {
            throw BizException.badRequest("库存不足");
        }
        item.setQuantity(quantity);
        cartItemMapper.updateById(item);
    }

    /** 勾选/取消勾选单项。 */
    @Transactional
    public void updateChecked(Long userId, Long id, int checked) {
        CartItem item = getOwned(userId, id);
        item.setChecked(checked == 1 ? 1 : 0);
        cartItemMapper.updateById(item);
    }

    /** 全选/取消全选。 */
    @Transactional
    public void checkAll(Long userId, int checked) {
        cartItemMapper.update(null, new LambdaUpdateWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .set(CartItem::getChecked, checked == 1 ? 1 : 0));
    }

    /** 删除单项。 */
    @Transactional
    public void delete(Long userId, Long id) {
        getOwned(userId, id);
        cartItemMapper.deleteById(id);
    }

    /** 删除已勾选项（下单成功后由 trade 模块调用）。 */
    @Transactional
    public void deleteChecked(Long userId) {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getChecked, 1));
    }

    /** 按 ID 获取并校验归属。 */
    private CartItem getOwned(Long userId, Long id) {
        CartItem item = cartItemMapper.selectById(id);
        if (item == null || !userId.equals(item.getUserId())) {
            throw BizException.notFound("购物车项不存在");
        }
        return item;
    }

    /**
     * 商品是否配置了「真实规格」。
     *
     * <p>种子数据里部分无规格商品也带了一条 {@code spec_value} 为空的 SKU 占位记录，
     * 这类记录不构成规格，不应强制用户选择。</p>
     */
    private boolean hasRealSpec(Long productId) {
        Long count = productSkuMapper.selectCount(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, productId)
                .isNotNull(ProductSku::getSpecValue)
                .ne(ProductSku::getSpecValue, ""));
        return count != null && count > 0;
    }

    /** 查询当前可用库存（有 SKU 取 SKU 库存，否则取商品库存）。 */
    private int availableStock(Long productId, Long skuId) {
        if (skuId != null && skuId > NO_SKU) {
            ProductSku sku = productSkuMapper.selectById(skuId);
            return sku == null ? 0 : nullToZero(sku.getStock());
        }
        Product product = productMapper.selectById(productId);
        return product == null ? 0 : nullToZero(product.getStock());
    }

    /** 组装购物车项视图，并在商品/规格异常时标记失效。 */
    private CartItemVO buildItemVo(CartItem item) {
        CartItemVO vo = new CartItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setSkuId(item.getSkuId());
        vo.setQuantity(item.getQuantity());
        vo.setChecked(item.getChecked());

        Product product = productMapper.selectById(item.getProductId());
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            vo.setProductName(product == null ? "已下架商品" : product.getName());
            vo.setProductImage(product == null ? null : product.getMainImage());
            vo.setPrice(BigDecimal.ZERO);
            vo.setStock(0);
            vo.setSubtotal(BigDecimal.ZERO);
            vo.setValid(false);
            vo.setInvalidReason("商品已下架");
            return vo;
        }

        BigDecimal price = product.getPrice();
        int stock = nullToZero(product.getStock());
        String image = product.getMainImage();
        boolean skuMissing = false;

        if (item.getSkuId() != null && item.getSkuId() > NO_SKU) {
            ProductSku sku = productSkuMapper.selectById(item.getSkuId());
            if (sku == null) {
                skuMissing = true;
            } else {
                price = sku.getPrice();
                stock = nullToZero(sku.getStock());
                vo.setSpecValue(sku.getSpecValue());
                if (sku.getSkuImage() != null && !sku.getSkuImage().isEmpty()) {
                    image = sku.getSkuImage();
                }
            }
        }

        vo.setProductName(product.getName());
        vo.setProductImage(image);
        vo.setPrice(price);
        vo.setStock(stock);

        if (skuMissing) {
            vo.setSubtotal(BigDecimal.ZERO);
            vo.setValid(false);
            vo.setInvalidReason("商品规格已下架");
        } else if (stock <= 0 || item.getQuantity() > stock) {
            vo.setSubtotal(BigDecimal.ZERO);
            vo.setValid(false);
            vo.setInvalidReason("库存不足");
        } else {
            vo.setSubtotal(price.multiply(BigDecimal.valueOf(item.getQuantity())));
            vo.setValid(true);
        }
        return vo;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
