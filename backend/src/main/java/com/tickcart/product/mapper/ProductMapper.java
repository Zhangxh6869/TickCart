package com.tickcart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * Product Mapper。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
