package com.tickcart.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.cart.entity.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * CartItem Mapper。
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 物理删除指定用户下「同商品同规格」的历史残留行（处于逻辑删除态的记录）。
     *
     * <p>唯一键 {@code uk_user_product_sku(user_id, product_id, sku_id)} 不含 {@code deleted} 列，
     * 而删除购物车项是逻辑删除、行仍留在表里。若不清掉残留，用户「删掉商品再加回来」
     * 时 INSERT 会撞唯一键直接报错。此处只清理已删除的行，不影响在用的购物车项。</p>
     *
     * @return 被清理的行数
     */
    @Delete("DELETE FROM cart_item WHERE user_id = #{userId} AND product_id = #{productId} "
            + "AND sku_id = #{skuId} AND deleted = 1")
    int purgeDeleted(@Param("userId") Long userId,
                     @Param("productId") Long productId,
                     @Param("skuId") Long skuId);
}
