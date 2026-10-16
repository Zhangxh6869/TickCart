package com.tickcart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.product.entity.UserViewHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * UserViewHistory Mapper。
 */
@Mapper
public interface UserViewHistoryMapper extends BaseMapper<UserViewHistory> {

    /**
     * 记录一次浏览（幂等 upsert）。
     *
     * <p>同一用户同一商品已有记录时只刷新 {@code view_time}，不产生新行；
     * 靠唯一键 {@code uk_user_product} 保证并发安全，不需要「先查后写」。</p>
     *
     * <p>时间直接用 {@code NOW()}（数据库时间），与 {@code DEFAULT CURRENT_TIMESTAMP} 同源，
     * 避免应用服务器与数据库时钟不一致导致排序错乱。
     * 也正因为走的是自定义 SQL，{@code MyMetaObjectHandler} 的自动填充不会生效，
     * 三个时间列必须显式赋值。</p>
     *
     * @return 影响行数（插入 1、更新 2，仅用于日志，不参与业务判断）
     */
    @Insert("""
            INSERT INTO user_view_history (user_id, product_id, view_time, created_at, updated_at)
            VALUES (#{userId}, #{productId}, NOW(), NOW(), NOW())
            ON DUPLICATE KEY UPDATE view_time = NOW(), updated_at = NOW()
            """)
    int upsert(@Param("userId") Long userId, @Param("productId") Long productId);
}
