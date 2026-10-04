package com.tickcart.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
