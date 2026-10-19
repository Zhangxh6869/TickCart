package com.tickcart.common.idempotent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.idempotent.entity.IdempotentRecord;
import com.tickcart.common.idempotent.mapper.IdempotentRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 幂等服务：用「唯一键占位」实现「同一请求只执行一次」。
 *
 * <p>使用方式（必须在业务方法的最前面调用）：</p>
 * <pre>{@code
 * String replayed = idempotentService.claim(userId, SCENE_ORDER_CREATE, idemKey);
 * if (replayed != null) {          // 重复请求，直接回放首次结果
 *     return replay(replayed);
 * }
 * ... 执行业务 ...
 * idempotentService.complete(userId, SCENE_ORDER_CREATE, idemKey, orderNo);
 * }</pre>
 *
 * <p><b>建议放在业务方法最前面</b>：一是失败快（重复请求不必先查地址、读购物车），
 * 二是占位记录尽早写入、尽早参与唯一键竞争，持锁时间最短。
 * 回查用的是加锁读（当前读），本身不受 RR 快照影响，所以这条不是正确性前提，
 * 而是性能与可读性上的约定。</p>
 *
 * <p><b>为什么冲突回查用 {@code FOR SHARE} 而不是 {@code FOR UPDATE}</b>：
 * INSERT 撞唯一键时，InnoDB 会在那条重复记录上给本事务加一把 <b>共享锁</b>（持有到事务结束）。
 * 如果回查再请求排他锁，N 个并发重复请求就各自攥着 S 锁去等别人的 X 锁 —— 直接环形死锁
 * （50 并发实测必现 {@code Deadlock found when trying to get lock}）。
 * 共享锁与已持有的 S 锁兼容，既拿得到，又仍是「当前读」（绕过快照、读到最新已提交版本）。</p>
 *
 * <p>本类不加 {@code @Transactional}：它必须加入调用方的事务，与业务同生共死
 * （业务回滚 → 占位记录一并回滚 → 允许重试）。</p>
 */
@Service
public class IdempotentService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentService.class);

    /** 场景：购物车结算下单 */
    public static final String SCENE_ORDER_CREATE = "order.create";

    /** 场景：立即购买下单 */
    public static final String SCENE_ORDER_BUY_NOW = "order.buyNow";

    /** 场景：秒杀建单（幂等键 = 抢购时生成的 requestId） */
    public static final String SCENE_SECKILL_BUY = "seckill.buy";

    /** 幂等键长度上限，与 {@code idempotent_record.idem_key} 列宽一致 */
    private static final int KEY_MAX_LENGTH = 64;

    private final IdempotentRecordMapper idempotentRecordMapper;
    private final long retentionHours;

    public IdempotentService(IdempotentRecordMapper idempotentRecordMapper,
                             @Value("${tickcart.idempotent.retention-hours:24}") long retentionHours) {
        this.idempotentRecordMapper = idempotentRecordMapper;
        this.retentionHours = retentionHours;
    }

    /**
     * 占位：抢本次请求的执行权。
     *
     * @param idemKey 客户端生成的幂等键；为空表示调用方未提供，退化为无幂等保护
     * @return {@code null} 表示占位成功、调用方应继续执行业务；
     *         非 {@code null} 表示重复请求，值为首次成功时的业务单号，调用方应直接回放
     * @throws BizException 幂等键超长、或重复请求对应的首次请求尚未完成
     */
    public String claim(Long userId, String scene, String idemKey) {
        if (!StringUtils.hasText(idemKey)) {
            // 兼容旧前端与回归脚本：不传就照常执行，但要留下痕迹便于排查重复下单
            log.warn("请求未携带幂等键，本次无幂等保护：userId={}, scene={}", userId, scene);
            return null;
        }
        String key = idemKey.trim();
        if (key.length() > KEY_MAX_LENGTH) {
            throw BizException.badRequest("幂等键长度不能超过 " + KEY_MAX_LENGTH + " 个字符");
        }

        IdempotentRecord record = new IdempotentRecord();
        record.setScene(scene);
        record.setUserId(userId);
        record.setIdemKey(key);
        record.setExpireAt(LocalDateTime.now().plusHours(retentionHours));
        try {
            idempotentRecordMapper.insert(record);
            return null;
        } catch (DuplicateKeyException e) {
            // 唯一键冲突 = 同一个键已经在用。MySQL 不会因单条语句失败而中止事务，
            // 因此这里可以继续回查（PostgreSQL 则不行，换库时要改成独立事务占位）。
            return replayOf(userId, scene, key);
        }
    }

    /**
     * 回填业务单号：标记本次请求已成功完成。
     *
     * <p>与 {@link #claim} 在同一事务内，提交后该记录才对其他请求可见。</p>
     */
    public void complete(Long userId, String scene, String idemKey, String bizNo) {
        if (!StringUtils.hasText(idemKey) || !StringUtils.hasText(bizNo)) {
            return;
        }
        idempotentRecordMapper.update(null, new LambdaUpdateWrapper<IdempotentRecord>()
                .eq(IdempotentRecord::getScene, scene)
                .eq(IdempotentRecord::getUserId, userId)
                .eq(IdempotentRecord::getIdemKey, idemKey.trim())
                .set(IdempotentRecord::getBizNo, bizNo));
    }

    /** 冲突后回查首次结果。 */
    private String replayOf(Long userId, String scene, String key) {
        IdempotentRecord exist = idempotentRecordMapper.selectOne(
                new LambdaQueryWrapper<IdempotentRecord>()
                        .eq(IdempotentRecord::getScene, scene)
                        .eq(IdempotentRecord::getUserId, userId)
                        .eq(IdempotentRecord::getIdemKey, key)
                        .last("FOR SHARE"));
        if (exist == null || !StringUtils.hasText(exist.getBizNo())) {
            // 理论上不可达：同事务下可见的行必然已回填单号。
            // 真出现说明有人绕过本服务直接写表，或幂等记录被外部清理，宁可让用户重试。
            log.warn("幂等键冲突但未取到业务单号：userId={}, scene={}, key={}", userId, scene, key);
            throw BizException.conflict("请求处理中，请稍后重试");
        }
        return exist.getBizNo();
    }
}
