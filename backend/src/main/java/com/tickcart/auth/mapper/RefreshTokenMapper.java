package com.tickcart.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.auth.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 刷新令牌 Mapper。
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
}
