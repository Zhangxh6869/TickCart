package com.tickcart.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.cart.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * CartItem Mapper。
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
