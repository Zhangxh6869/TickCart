package com.tickcart.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.seckill.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * SeckillActivity Mapper。
 */
@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /**
     * 原子累加已售数量（建单成功后由消费者调用）。
     *
     * <p>用 {@code sold_count = sold_count + n} 而不是「先查后写」：并发建单时
     * 后者会互相覆盖。带 {@code sold_count + n <= seckill_stock} 的上限条件，
     * 保证即使 Redis 与 DB 短暂不一致，DB 侧也不会记出「超卖」的账。</p>
     *
     * @return 影响行数；0 表示已达上限（调用方应记 WARN 并核对 Redis）
     */
    @Update("UPDATE seckill_activity SET sold_count = sold_count + #{quantity} "
            + "WHERE id = #{activityId} AND sold_count + #{quantity} <= seckill_stock")
    int increaseSoldCount(@Param("activityId") Long activityId, @Param("quantity") int quantity);

    /**
     * 回补已售数量（取消/超时关单时调用）。
     *
     * <p>不下溢：{@code sold_count - n} 小于 0 时按 0 处理，避免脏数据把计数打成负数。</p>
     */
    @Update("UPDATE seckill_activity SET sold_count = GREATEST(sold_count - #{quantity}, 0) "
            + "WHERE id = #{activityId}")
    int decreaseSoldCount(@Param("activityId") Long activityId, @Param("quantity") int quantity);
}
