package com.tickcart.common.idempotent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tickcart.common.idempotent.entity.IdempotentRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * IdempotentRecord Mapper。
 */
@Mapper
public interface IdempotentRecordMapper extends BaseMapper<IdempotentRecord> {

    /**
     * 物理删除过期记录。
     *
     * <p>幂等记录只增不删会随下单量线性增长（唯一键索引也会变大），
     * 由定时任务按 {@code expire_at} 清理。用物理删除而非逻辑删除：
     * 这些行只是「去重凭据」，没有追溯价值，留着反而会一直占着唯一键。</p>
     *
     * @return 被删除的行数
     */
    @Delete("DELETE FROM idempotent_record WHERE expire_at < #{now}")
    int deleteExpired(@Param("now") LocalDateTime now);
}
