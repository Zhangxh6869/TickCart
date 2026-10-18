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
import com.tickcart.product.service.ProductService;
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
    private final ProductService productService;

    public CartService(CartItemMapper cartItemMapper,
                       ProductMapper productMapper,
                       ProductSkuMapper productSkuMapper,
                       ProductService productService) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.productService = productService;
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
        } else if (productService.hasRealSpec(product.getId())) {
            // 商品配置了规格却不传 skuId 时，若放行会走商品级库存校验，
            // 导致 SKU 库存与商品库存各算一套，下单扣减错乱
            throw BizException.badRequest("请选择商品规格");
        }

        int quantity = req.getQuantity() == null ? 1 : req.getQuantity();
        if (quantity <= 0) {
            throw BizException.badRequest("购买数量必须大于 0");
        }

        // 唯一键不含 deleted 列：先物理清掉同规格的历史逻辑删除残留，
        // 否则用户「删掉再加回来」会撞唯一键报错
        cartItemMapper.purgeDeleted(userId, product.getId(), skuId);

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

    /**
     * 切换购物车项的规格（同商品换成另一个规格）。
     *
     * <p>规则：</p>
     * <ul>
     *   <li><b>幂等</b>：目标规格与当前一致时不做任何改动，避免无意义的写库</li>
     *   <li><b>库存</b>：目标规格库存不足以覆盖当前数量时拒绝，保持原规格不变
     *       （否则用户会在没察觉的情况下买到超出库存的数量）</li>
     *   <li><b>合并</b>：目标规格在购物车里已有另一条时合并为一条（数量相加），
     *       与加购的合并口径一致，避免出现两条同规格记录；合并后勾选状态取两条的并集，
     *       防止刚切换的项掉出结算范围</li>
     *   <li><b>归属</b>：校验 {@code user_id}，越权按不存在处理</li>
     * </ul>
     */
    @Transactional
    public void changeSku(Long userId, Long id, Long skuId) {
        CartItem item = getOwned(userId, id);
        if (skuId == null || skuId <= NO_SKU) {
            throw BizException.badRequest("商品规格不存在");
        }
        // 相同款式：保持原样
        if (skuId.equals(item.getSkuId())) {
            return;
        }

        Product product = productMapper.selectById(item.getProductId());
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw BizException.badRequest("商品不存在或已下架");
        }

        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null || !product.getId().equals(sku.getProductId())) {
            throw BizException.badRequest("商品规格不存在");
        }
        if (nullToZero(sku.getStock()) < item.getQuantity()) {
            throw BizException.badRequest("库存不足");
        }

        // 唯一键 uk_user_product_sku 不含 deleted 列：目标规格若存在历史逻辑删除残留，
        // 会占用唯一键组合，导致下面的 UPDATE 撞键失败。先物理清掉，与 add 同一处理。
        cartItemMapper.purgeDeleted(userId, product.getId(), skuId);

        // 同商品同规格已存在另一条时合并，避免出现两条重复记录
        CartItem sameSkuItem = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, product.getId())
                .eq(CartItem::getSkuId, skuId)
                .ne(CartItem::getId, item.getId()));
        if (sameSkuItem == null) {
            item.setSkuId(skuId);
            cartItemMapper.updateById(item);
            return;
        }

        int merged = sameSkuItem.getQuantity() + item.getQuantity();
        if (merged > MAX_QUANTITY_PER_ITEM) {
            throw BizException.badRequest("单次最多购买 " + MAX_QUANTITY_PER_ITEM + " 件");
        }
        sameSkuItem.setQuantity(merged);
        // 勾选取并集：用户刚操作的那条若原本勾选，合并后不应掉出结算范围
        if (Integer.valueOf(1).equals(item.getChecked())) {
            sameSkuItem.setChecked(1);
        }
        cartItemMapper.updateById(sameSkuItem);
        cartItemMapper.deleteById(item.getId());
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

    /** 删除当前所有已勾选项（购物车页「删除选中」用；下单链路已改用 {@link #claimForCheckout}）。 */
    @Transactional
    public void deleteChecked(Long userId) {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getChecked, 1));
    }

    /**
     * 结算抢占：把本次要结算的购物车项标记删除，返回实际影响行数。
     *
     * <p>这是「同一批购物车只能被结算一次」的唯一闸门。并发下只有一个请求能影响全部行
     * （返回 {@code ids.size()}），其余返回 0 —— 调用方据此判定「已被其他请求结算」并抛 409。</p>
     *
     * <p>抢占必须发生在扣库存<b>之前</b>：失败路径不碰库存行，锁竞争更小、失败更快。</p>
     *
     * @param ids 本次结算的<b>有效</b>购物车项 ID
     * @return 实际被标记删除的行数，调用方须校验是否等于 {@code ids.size()}
     */
    @Transactional
    public int claimForCheckout(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return cartItemMapper.markDeleted(userId, ids);
    }

    /**
     * 下单后清理「本次读到的失效勾选项」。
     *
     * <p>与 {@link #deleteChecked} 的区别：按精确 id 删除、不校验行数。
     * {@code deleteChecked} 按「当前 checked = 1」删，会把下单过程中用户新勾选的其他商品
     * 一并删掉；这里只动本次确实读到的那些行。</p>
     */
    @Transactional
    public void removeItems(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        cartItemMapper.markDeleted(userId, ids);
    }

    /** 按 ID 获取并校验归属。 */
    private CartItem getOwned(Long userId, Long id) {
        CartItem item = cartItemMapper.selectById(id);
        if (item == null || !userId.equals(item.getUserId())) {
            throw BizException.notFound("购物车项不存在");
        }
        return item;
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
