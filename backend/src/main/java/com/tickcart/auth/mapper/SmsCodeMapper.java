package com.tickcart.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.auth.entity.SmsCode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信验证码 Mapper。
 */
@Mapper
public interface SmsCodeMapper extends BaseMapper<SmsCode> {
}
