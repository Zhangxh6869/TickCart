package com.tickcart.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tickcart.seckill.cache.SeckillActivitySnapshot;
import com.tickcart.seckill.cache.SeckillKeys;
import com.tickcart.seckill.entity.SeckillActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SeckillStockService} 单元测试。
 *
 * <p>Lua 脚本语义由运行时并发验证覆盖；这里钉住的是「Redis 操作姿势」——
 * 库存必须 SETNX、快照必须刷新、回补必须判存在。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("秒杀库存服务")
class SeckillStockServiceTest {

    private static final Long ACTIVITY_ID = 9L;
    private static final Long USER_ID = 2L;

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private SeckillStockService service;
    /** 与 Spring 注入的 mapper 对齐：必须注册 JSR310，否则 LocalDateTime 无法序列化 */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new SeckillStockService(redis, objectMapper);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("预热：库存用 SETNX，快照用普通 SET（快照必须可刷新）")
    void preloadWritesStockWithNxAndMetaWithSet() {
        SeckillActivity activity = activity(100, 0);
        when(valueOps.setIfAbsent(eq(SeckillKeys.stock(ACTIVITY_ID)), eq("100"), any(Duration.class)))
                .thenReturn(true);

        service.preload(activity);

        // 快照：覆盖写（下线后重新上线要能刷新 enabled）
        verify(valueOps).set(eq(SeckillKeys.activity(ACTIVITY_ID)), anyString(), any(Duration.class));
        // 库存：不存在才写，避免把「已扣未建单」的库存还回去
        verify(valueOps).setIfAbsent(eq(SeckillKeys.stock(ACTIVITY_ID)), eq("100"), any(Duration.class));
    }

    @Test
    @DisplayName("预热：剩余库存 = 总库存 - 已售")
    void preloadUsesRemainingStock() {
        SeckillActivity activity = activity(100, 30);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service.preload(activity);

        verify(valueOps).setIfAbsent(eq(SeckillKeys.stock(ACTIVITY_ID)), eq("70"), any(Duration.class));
    }

    @Test
    @DisplayName("已结束的活动：只刷新快照让「已结束」尽快生效，不建库存")
    void preloadEndedActivityRefreshesSnapshotOnly() {
        SeckillActivity activity = activity(100, 0);
        activity.setEndTime(LocalDateTime.now().minusHours(2));

        service.preload(activity);

        verify(valueOps).set(eq(SeckillKeys.activity(ACTIVITY_ID)), anyString(), any(Duration.class));
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("快照读取：未预热返回 null")
    void snapshotMissingReturnsNull() {
        when(valueOps.get(SeckillKeys.activity(ACTIVITY_ID))).thenReturn(null);

        assertThat(service.snapshot(ACTIVITY_ID)).isNull();
    }

    @Test
    @DisplayName("快照读取：解析成功")
    void snapshotParsed() throws Exception {
        SeckillActivitySnapshot snapshot = SeckillActivitySnapshot.from(activity(100, 0));
        when(valueOps.get(SeckillKeys.activity(ACTIVITY_ID)))
                .thenReturn(objectMapper.writeValueAsString(snapshot));

        SeckillActivitySnapshot loaded = service.snapshot(ACTIVITY_ID);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getActivityId()).isEqualTo(ACTIVITY_ID);
        assertThat(loaded.getSeckillPrice()).isEqualByComparingTo("9.90");
    }

    @Test
    @DisplayName("扣减：脚本返回值原样透出")
    @SuppressWarnings("unchecked")
    void tryDeductPassesThroughScriptResult() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(7L);

        assertThat(service.tryDeduct(ACTIVITY_ID, USER_ID, 1, 2)).isEqualTo(7L);
    }

    @Test
    @DisplayName("扣减：脚本返回 null 按未预热处理")
    @SuppressWarnings("unchecked")
    void tryDeductNullMeansNotPreloaded() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(null);

        assertThat(service.tryDeduct(ACTIVITY_ID, USER_ID, 1, 2))
                .isEqualTo(SeckillStockService.RESULT_NOT_PRELOADED);
    }

    @Test
    @DisplayName("回补：库存 key 存在才回补")
    void restoreOnlyWhenKeyExists() {
        when(redis.hasKey(SeckillKeys.stock(ACTIVITY_ID))).thenReturn(true);

        service.restore(ACTIVITY_ID, USER_ID, 2);

        verify(valueOps).increment(SeckillKeys.stock(ACTIVITY_ID), 2);
        verify(valueOps).decrement(SeckillKeys.bought(ACTIVITY_ID, USER_ID), 2);
    }

    @Test
    @DisplayName("回补：库存 key 已过期时跳过，不凭空创建库存")
    void restoreSkipsWhenKeyGone() {
        when(redis.hasKey(SeckillKeys.stock(ACTIVITY_ID))).thenReturn(false);

        service.restore(ACTIVITY_ID, USER_ID, 2);

        verify(valueOps, never()).increment(anyString(), anyLong());
    }

    @Test
    @DisplayName("请求防重：SETNX 失败表示有请求在处理")
    void requestLockUsesSetNx() {
        when(valueOps.setIfAbsent(eq(SeckillKeys.request(ACTIVITY_ID, USER_ID)), eq("rid"), any(Duration.class)))
                .thenReturn(false);

        assertThat(service.tryLockRequest(ACTIVITY_ID, USER_ID, "rid")).isFalse();
    }

    @Test
    @DisplayName("刷新快照：不改库存，只覆盖活动配置")
    void refreshSnapshotOnlyTouchesMeta() {
        service.refreshSnapshot(activity(100, 0));

        verify(valueOps).set(eq(SeckillKeys.activity(ACTIVITY_ID)), anyString(), any(Duration.class));
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("删除活动：清掉快照与库存，避免被删活动还能抢")
    void evictRemovesBothKeys() {
        service.evict(ACTIVITY_ID);

        verify(redis).delete(List.of(SeckillKeys.activity(ACTIVITY_ID), SeckillKeys.stock(ACTIVITY_ID)));
    }

    private SeckillActivity activity(int stock, int sold) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setProductId(302L);
        activity.setSkuId(490L);
        activity.setSeckillPrice(new BigDecimal("9.90"));
        activity.setSeckillStock(stock);
        activity.setSoldCount(sold);
        activity.setLimitPerUser(2);
        activity.setStartTime(LocalDateTime.now().minusMinutes(5));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        activity.setEnabled(1);
        return activity;
    }
}
