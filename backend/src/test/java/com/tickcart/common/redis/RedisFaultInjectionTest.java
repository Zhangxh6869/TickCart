package com.tickcart.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickcart.common.cache.CacheProperties;
import com.tickcart.common.cache.CacheService;
import com.tickcart.common.ratelimit.RedisRateLimiter;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 故障注入：**Redis 连得上但永远不回应**时的降级行为。
 *
 * <p><b>为什么不用 mock</b>：现有单测是 {@code doThrow(new RedisConnectionFailureException())}
 * —— 异常是代码里写死的，「真实超时到底多久」测不出来。而本项目的降级是否可用，
 * 恰恰取决于超时长度：3 秒超时时商品详情要 6 秒（限流器 + 缓存各一次），
 * 等于「降级成功但服务不可用」。</p>
 *
 * <p><b>怎么造故障</b>：起一个只 {@code accept()} 不读不写的 {@link ServerSocket}。
 * 客户端能连上（不会秒失败），但每个命令都要等到超时 —— 这与
 * {@code docker stop} 后「连接被拒、立即失败」不同，也是线上更常见的那种「网络黑洞」。
 * 只有这种故障才能暴露超时叠加。</p>
 *
 * <p><b>不断言精确耗时</b>：CI 机器慢，精确到毫秒必然 flaky。这里只按数量级断言 ——
 * 「约等于一个超时」和「约等于两个超时」，够用且稳。</p>
 */
@DisplayName("故障注入 · Redis 无响应")
class RedisFaultInjectionTest {

    /** 命令超时。与 application.yml 的 500ms 对齐 */
    private static final long TIMEOUT_MS = 500;

    /** 断言容差：下限留 100ms 余量，上限给 3 倍（CI 慢机器） */
    private static final long LOWER = TIMEOUT_MS - 100;
    private static final long UPPER = TIMEOUT_MS * 3;

    private static ServerSocket blackhole;
    private static ExecutorService acceptor;
    /** 收下的连接必须持有，关掉就等于「连接被断开」，故障性质就变了 */
    private static final List<Socket> HELD = new CopyOnWriteArrayList<>();

    private final List<LettuceConnectionFactory> factories = new ArrayList<>();

    @BeforeAll
    static void startBlackhole() throws IOException {
        blackhole = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        acceptor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "fake-redis-blackhole");
            thread.setDaemon(true);
            return thread;
        });
        acceptor.submit(() -> {
            while (!blackhole.isClosed()) {
                try {
                    HELD.add(blackhole.accept());
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    @AfterAll
    static void stopBlackhole() throws IOException {
        blackhole.close();
        for (Socket socket : HELD) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 收尾阶段忽略
            }
        }
        acceptor.shutdownNow();
    }

    @AfterEach
    void closeFactories() {
        factories.forEach(LettuceConnectionFactory::destroy);
        factories.clear();
    }

    /** 指向黑洞端口、命令超时为 {@link #TIMEOUT_MS} 的客户端。 */
    private StringRedisTemplate blackholedRedis() {
        RedisStandaloneConfiguration standalone =
                new RedisStandaloneConfiguration("127.0.0.1", blackhole.getLocalPort());
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(TIMEOUT_MS))
                .clientOptions(ClientOptions.builder()
                        .autoReconnect(false)
                        .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(TIMEOUT_MS)))
                        .build())
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, client);
        factory.afterPropertiesSet();
        factory.start();
        factories.add(factory);
        return new StringRedisTemplate(factory);
    }

    /** 永不熔断的守卫，用于单独观察「降级耗时」。 */
    private RedisHealthGuard neverTrips() {
        return new RedisHealthGuard(true, 99, 30_000L, 5_000L);
    }

    private CacheService cacheWith(StringRedisTemplate redis, RedisHealthGuard guard) {
        return new CacheService(redis, new CacheProperties(), new ObjectMapper(), guard);
    }

    @Test
    @DisplayName("读缓存超时：回源返回数据，耗时约等于一个超时（不是成倍叠加）")
    void cacheDegradesWithinSingleTimeout() {
        CacheService cache = cacheWith(blackholedRedis(), neverTrips());

        long start = System.nanoTime();
        String value = cache.get("product:detail:302", 60, String.class, () -> "from-db");
        long elapsed = elapsedMs(start);

        assertThat(value).as("降级后必须拿到回源数据，不能让详情页 500").isEqualTo("from-db");
        assertThat(elapsed)
                .as("耗时应约等于一个超时（%dms），实际 %dms —— 若接近 3 倍说明超时叠加了", TIMEOUT_MS, elapsed)
                .isBetween(LOWER, UPPER);
    }

    @Test
    @DisplayName("限流器超时：fail-open 放行，不把一次 Redis 抖动放大成全站 503")
    void rateLimiterFailsOpen() {
        RedisRateLimiter limiter = new RedisRateLimiter(blackholedRedis(), neverTrips());

        long start = System.nanoTime();
        long waitMs = limiter.tryAcquire("search:ip:1.2.3.4", 10, 1);
        long elapsed = elapsedMs(start);

        assertThat(waitMs).as("0 表示放行").isZero();
        assertThat(elapsed).isBetween(LOWER, UPPER);
    }

    @Test
    @DisplayName("★ 一个请求碰两次 Redis：两次超时叠加，总耗时约等于两个超时")
    void twoRedisHopsStackUp() {
        StringRedisTemplate redis = blackholedRedis();
        RedisRateLimiter limiter = new RedisRateLimiter(redis, neverTrips());
        CacheService cache = cacheWith(redis, neverTrips());

        long start = System.nanoTime();
        limiter.tryAcquire("product:ip:1.2.3.4", 60, 1);
        cache.get("product:detail:302", 60, String.class, () -> "from-db");
        long elapsed = elapsedMs(start);

        // 这正是 2026-10-27 实测到 6.06 秒（超时 3s 时）的原因：
        // 不是「读缓存 + 写缓存」，而是「限流器 + 缓存」两个独立环节各付一次超时
        assertThat(elapsed)
                .as("两次独立超时叠加，实际 %dms（单个超时 %dms）", elapsed, TIMEOUT_MS)
                .isBetween(TIMEOUT_MS, UPPER * 2);
    }

    @Test
    @DisplayName("★ 熔断打开后：不再发起连接，耗时骤降到毫秒级")
    void circuitOpenStopsPayingTheTimeout() {
        RedisHealthGuard guard = new RedisHealthGuard(true, 2, 30_000L, 5_000L);
        CacheService cache = cacheWith(blackholedRedis(), guard);

        // 前两次真实超时，把失败计数推到阈值
        cache.get("k1", 60, String.class, () -> "db");
        cache.get("k2", 60, String.class, () -> "db");
        assertThat(guard.isOpen()).as("连续失败达到阈值后应熔断").isTrue();

        long start = System.nanoTime();
        String value = cache.get("k3", 60, String.class, () -> "db");
        long elapsed = elapsedMs(start);

        assertThat(value).isEqualTo("db");
        assertThat(elapsed)
                .as("熔断期间不该再等超时，实际 %dms（未熔断时约 %dms）", elapsed, TIMEOUT_MS)
                .isLessThan(100);
    }

    @Test
    @DisplayName("熔断后限流器同样直接放行，不再尝试连接")
    void circuitAlsoShortCircuitsRateLimiter() {
        RedisHealthGuard guard = new RedisHealthGuard(true, 2, 30_000L, 5_000L);
        RedisRateLimiter limiter = new RedisRateLimiter(blackholedRedis(), guard);

        limiter.tryAcquire("k1", 10, 1);
        limiter.tryAcquire("k2", 10, 1);
        assertThat(guard.isOpen()).isTrue();

        long start = System.nanoTime();
        long waitMs = limiter.tryAcquire("k3", 10, 1);
        long elapsed = elapsedMs(start);

        assertThat(waitMs).isZero();
        assertThat(elapsed).isLessThan(100);
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
