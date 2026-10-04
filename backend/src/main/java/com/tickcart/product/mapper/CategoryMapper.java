package com.tickcart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.product.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * Category Mapper。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
