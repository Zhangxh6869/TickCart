package com.tickcart.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickcart.seckill.cache.SeckillKeys;
import com.tickcart.seckill.cache.SeckillResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 抢购结果的读写（前端轮询用）。
 *
 * <p>结果是「一次抢购的最终交代」：成功带订单号，失败带原因（如「请先设置收货地址」）。
 * 没有它，用户抢完只能干等，或者去订单列表里翻。</p>
 */
@Service
public class SeckillResultService {

    private static final Logger log = LoggerFactory.getLogger(SeckillResultService.class);

    /** 结果保留 24 小时：比活动 TTL 长，用户可能第二天才回来查 */
    private static final Duration RESULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public SeckillResultService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** 写成功结果。失败只记日志：结果查不到时前端还能引导去订单列表，不该影响建单。 */
    public void success(String requestId, Long userId, String orderNo) {
        write(SeckillResult.success(requestId, userId, orderNo));
    }

    /** 写失败结果（库存已回补，用户可重新抢）。 */
    public void failed(String requestId, Long userId, String message) {
        write(SeckillResult.failed(requestId, userId, message));
    }

    /**
     * 读取结果。
     *
     * @return 不存在、已过期、或不属于该用户时返回 {@code null}
     *         —— 三种情况统一按 404 处理，不暴露「别人的结果存在」这个信息
     */
    public SeckillResult get(String requestId, Long userId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(SeckillKeys.result(requestId));
            if (json == null) {
                return null;
            }
            SeckillResult result = objectMapper.readValue(json, SeckillResult.class);
            return userId != null && userId.equals(result.getUserId()) ? result : null;
        } catch (Exception e) {
            log.warn("读取秒杀结果失败：requestId={}", requestId, e);
            return null;
        }
    }

    private void write(SeckillResult result) {
        try {
            redis.opsForValue().set(SeckillKeys.result(result.getRequestId()),
                    objectMapper.writeValueAsString(result), RESULT_TTL);
        } catch (Exception e) {
            log.error("写入秒杀结果失败：requestId={}", result.getRequestId(), e);
        }
    }
}
