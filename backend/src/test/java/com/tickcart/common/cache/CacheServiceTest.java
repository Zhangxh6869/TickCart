package com.tickcart.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CacheService} 单元测试。
 *
 * <p>覆盖：命中、未命中回填、空值防穿透、总开关、Redis 故障降级、TTL 抖动、失效删除。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("读缓存")
class CacheServiceTest {

    private static final String KEY = "demo";
    private static final String FULL_KEY = "cache:demo";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private CacheProperties properties;
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        properties = new CacheProperties();
        cacheService = new CacheService(redis, properties, new ObjectMapper());
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        // 默认抢锁成功，让用例走「回源并回填」主路径（否则每个用例都要多等 150ms）
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
    }

    @Test
    @DisplayName("未命中：回源并回填；再次读取不再回源")
    void missThenHit() {
        AtomicInteger loads = new AtomicInteger();
        when(valueOps.get(FULL_KEY)).thenReturn(null, "{\"name\":\"牛油果\",\"id\":302}");

        Sample first = cacheService.get(KEY, 60, Sample.class, () -> {
            loads.incrementAndGet();
            return sample();
        });
        Sample second = cacheService.get(KEY, 60, Sample.class, () -> {
            loads.incrementAndGet();
            return sample();
        });

        assertThat(first.getName()).isEqualTo("牛油果");
        assertThat(second.getId()).isEqualTo(302L);
        assertThat(loads).hasValue(1);
        verify(valueOps).set(eq(FULL_KEY), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("空值：写入空值标记，重复请求不再回源（防穿透）")
    void nullValueCachesMarker() {
        AtomicInteger loads = new AtomicInteger();
        when(valueOps.get(FULL_KEY)).thenReturn(null);
        when(redis.hasKey(FULL_KEY + ":null")).thenReturn(false, true);

        assertThat(cacheService.get(KEY, 60, Sample.class, () -> {
            loads.incrementAndGet();
            return null;
        })).isNull();
        assertThat(cacheService.get(KEY, 60, Sample.class, () -> {
            loads.incrementAndGet();
            return null;
        })).isNull();

        assertThat(loads).hasValue(1);
        verify(valueOps).set(eq(FULL_KEY + ":null"), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("总开关关闭：每次都回源，不碰 Redis")
    void disabledBypassesCache() {
        properties.setEnabled(false);
        AtomicInteger loads = new AtomicInteger();

        cacheService.get(KEY, 60, Sample.class, () -> {
            loads.incrementAndGet();
            return sample();
        });
        cacheService.get(KEY, 60, Sample.class, () -> {
            loads.incrementAndGet();
            return sample();
        });

        assertThat(loads).hasValue(2);
        verify(valueOps, never()).get(anyString());
    }

    @Test
    @DisplayName("Redis 故障：降级回源，不向上抛异常")
    void redisFailureFallsBack() {
        doThrow(new RedisConnectionFailureException("down")).when(redis).opsForValue();

        Sample result = cacheService.get(KEY, 60, Sample.class, this::sample);

        assertThat(result.getName()).isEqualTo("牛油果");
    }

    @Test
    @DisplayName("loader 的业务异常照常上抛，不被当成缓存故障吞掉")
    void loaderExceptionPropagates() {
        when(valueOps.get(FULL_KEY)).thenReturn(null);
        when(redis.hasKey(FULL_KEY + ":null")).thenReturn(false);

        assertThatCode(() -> cacheService.get(KEY, 60, Sample.class, () -> {
            throw new IllegalStateException("商品不存在");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("商品不存在");
    }

    @Test
    @DisplayName("TTL 带 ±10% 抖动")
    void ttlJittered() {
        when(valueOps.get(FULL_KEY)).thenReturn(null);

        cacheService.get(KEY, 60, Sample.class, this::sample);

        ArgumentCaptor<Duration> captor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq(FULL_KEY), anyString(), captor.capture());
        assertThat(captor.getValue().getSeconds()).isBetween(54L, 66L);
    }

    @Test
    @DisplayName("失效：同时删除数据 key 与空值标记")
    @SuppressWarnings("unchecked")
    void evictDeletesBothKeys() {
        cacheService.evict(KEY);

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(redis).delete(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(FULL_KEY, FULL_KEY + ":null");
    }

    @Test
    @DisplayName("失效失败只记日志，不影响调用方")
    void evictSwallowsFailure() {
        when(redis.delete(any(Collection.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> cacheService.evict(KEY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("列表缓存按元素类型反序列化")
    void listCache() {
        when(valueOps.get("cache:hot")).thenReturn("[\"牛仔裤\",\"手机\"]");

        List<String> hot = cacheService.getList("hot", 300, String.class, List::of);

        assertThat(hot).containsExactly("牛仔裤", "手机");
    }

    private Sample sample() {
        Sample s = new Sample();
        s.name = "牛油果";
        s.id = 302L;
        return s;
    }

    /** 与 ProductDetail 同构：字段私有、只有 getter、没有 setter。 */
    static class Sample {
        private String name;
        private Long id;

        public String getName() {
            return name;
        }

        public Long getId() {
            return id;
        }
    }
}
