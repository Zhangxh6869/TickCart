package com.tickcart.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.trade.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * Order Mapper。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
