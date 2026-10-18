package com.tickcart.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.cart.entity.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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

    /**
     * 把指定购物车项标记删除（结算抢占 / 下单后清理失效项共用）。
     *
     * <p>写自定义 SQL 而不是走 {@code BaseMapper.update}：一是需要 {@code deleted = 0}
     * 作为条件参与行锁竞争，二是要拿到精确的影响行数用于判断「是否抢到」——
     * MyBatis-Plus 的逻辑删除会把 {@code deleted} 从 SET 里摘掉，达不到这个效果。</p>
     *
     * <p><b>并发语义</b>：{@code WHERE deleted = 0} 是当前读 + 行锁。同一批 id 的并发 UPDATE
     * 会串行化，只有第一个能影响全部行，其余影响 0 行 —— 调用方据此判定「已被别人结算」。</p>
     *
     * @return 实际被标记删除的行数
     */
    @Update("<script>"
            + "UPDATE cart_item SET deleted = 1 "
            + "WHERE user_id = #{userId} AND deleted = 0 AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int markDeleted(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
