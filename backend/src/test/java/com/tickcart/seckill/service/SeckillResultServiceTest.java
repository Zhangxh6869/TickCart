package com.tickcart.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tickcart.seckill.cache.SeckillKeys;
import com.tickcart.seckill.cache.SeckillResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SeckillResultService} 单元测试。
 *
 * <p>重点是归属校验：结果里带订单号，不能让别人凭 requestId 查到。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("秒杀结果服务")
class SeckillResultServiceTest {

    private static final String REQUEST_ID = "rid-1";
    private static final Long USER_ID = 2L;

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private SeckillResultService service;
    /** 与 Spring 注入的 mapper 对齐：必须注册 JSR310 */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new SeckillResultService(redis, objectMapper);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("写成功结果：带订单号，TTL 24h")
    void writeSuccess() {
        service.success(REQUEST_ID, USER_ID, "SK123");

        verify(valueOps).set(eq(SeckillKeys.result(REQUEST_ID)), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("读结果：属于本人时返回")
    void readOwnResult() throws Exception {
        when(valueOps.get(SeckillKeys.result(REQUEST_ID)))
                .thenReturn(objectMapper.writeValueAsString(
                        SeckillResult.success(REQUEST_ID, USER_ID, "SK123")));

        SeckillResult result = service.get(REQUEST_ID, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getOrderNo()).isEqualTo("SK123");
        assertThat(result.getStatus()).isEqualTo(SeckillResult.STATUS_SUCCESS);
    }

    @Test
    @DisplayName("读结果：属于他人时按不存在处理（不暴露订单号）")
    void readOthersResultReturnsNull() throws Exception {
        when(valueOps.get(SeckillKeys.result(REQUEST_ID)))
                .thenReturn(objectMapper.writeValueAsString(
                        SeckillResult.success(REQUEST_ID, USER_ID, "SK123")));

        assertThat(service.get(REQUEST_ID, 999L)).isNull();
    }

    @Test
    @DisplayName("读结果：不存在时返回 null")
    void readMissingReturnsNull() {
        when(valueOps.get(SeckillKeys.result(REQUEST_ID))).thenReturn(null);

        assertThat(service.get(REQUEST_ID, USER_ID)).isNull();
    }

    @Test
    @DisplayName("读结果：requestId 为空直接返回 null，不查 Redis")
    void readBlankRequestId() {
        assertThat(service.get("  ", USER_ID)).isNull();
    }
}
