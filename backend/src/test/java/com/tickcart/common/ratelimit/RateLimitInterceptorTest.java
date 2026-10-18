package com.tickcart.common.ratelimit;

import com.tickcart.common.exception.RateLimitedException;
import com.tickcart.common.security.UserContext;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RateLimitInterceptor} 单元测试。
 *
 * <p>覆盖：规则匹配（路径/方法/最长优先）、维度解析（IP / USER / 未登录回退）、
 * 超限抛 429、总开关。</p>
 */
@DisplayName("限流拦截器")
class RateLimitInterceptorTest {

    private static final String IP = "1.2.3.4";

    private RateLimitProperties properties;
    private RedisRateLimiter limiter;
    private RateLimitInterceptor interceptor;
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        limiter = mock(RedisRateLimiter.class);
        interceptor = new RateLimitInterceptor(properties, limiter);
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("没有命中任何规则：放行且不碰限流器")
    void noRulePasses() {
        properties.setRules(List.of());

        assertThat(interceptor.preHandle(request("GET", "/api/cart"), response, new Object())).isTrue();
        verifyNoInteractions(limiter);
    }

    @Test
    @DisplayName("路径命中但方法不符：放行（同一路径 GET 列表不应被 POST 规则限流）")
    void methodMismatchPasses() {
        properties.setRules(List.of(
                rule("order-create", "/api/orders", List.of("POST"), RateLimitRule.Dimension.USER, 5)));

        assertThat(interceptor.preHandle(request("GET", "/api/orders"), response, new Object())).isTrue();
        verifyNoInteractions(limiter);
    }

    @Test
    @DisplayName("IP 维度超限：抛 429，等待秒数由毫秒向上取整")
    void ipDimensionThrows() {
        properties.setRules(List.of(
                rule("search", "/api/search/**", List.of("GET"), RateLimitRule.Dimension.IP, 10)));
        when(limiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(1500L);

        RateLimitedException ex = catchThrowableOfType(
                () -> interceptor.preHandle(request("GET", "/api/search/hot"), response, new Object()),
                RateLimitedException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getRetryAfterSeconds()).isEqualTo(2L);
        verify(limiter).tryAcquire("search:ip:" + IP, 10, 1);
    }

    @Test
    @DisplayName("USER 维度已登录：桶按 userId 隔离")
    void userDimensionUsesUserId() {
        UserContext.set(claimsOf("2"));
        properties.setRules(List.of(
                rule("order-create", "/api/orders", List.of("POST"), RateLimitRule.Dimension.USER, 5)));
        when(limiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(0L);

        assertThat(interceptor.preHandle(request("POST", "/api/orders"), response, new Object())).isTrue();
        verify(limiter).tryAcquire("order-create:user:2", 5, 1);
    }

    @Test
    @DisplayName("USER 维度未登录：回退到 IP，不与空 userId 共桶")
    void userDimensionFallsBackToIp() {
        properties.setRules(List.of(
                rule("order-create", "/api/orders", List.of("POST"), RateLimitRule.Dimension.USER, 5)));
        when(limiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(0L);

        assertThat(interceptor.preHandle(request("POST", "/api/orders"), response, new Object())).isTrue();
        verify(limiter).tryAcquire("order-create:ip:" + IP, 5, 1);
    }

    @Test
    @DisplayName("多条规则命中：取路径最长的那条，不叠加计数")
    void longestPatternWins() {
        properties.setRules(List.of(
                rule("order-create", "/api/orders/**", List.of("POST"), RateLimitRule.Dimension.USER, 5),
                rule("buy-now", "/api/orders/buy-now", List.of("POST"), RateLimitRule.Dimension.USER, 1)));
        when(limiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(0L);

        interceptor.preHandle(request("POST", "/api/orders/buy-now"), response, new Object());

        verify(limiter).tryAcquire(eq("buy-now:ip:" + IP), eq(1), eq(1));
    }

    @Test
    @DisplayName("总开关关闭：全部放行")
    void disabledPassesAll() {
        properties.setEnabled(false);
        properties.setRules(List.of(
                rule("search", "/api/search/**", List.of("GET"), RateLimitRule.Dimension.IP, 10)));

        assertThat(interceptor.preHandle(request("GET", "/api/search/hot"), response, new Object())).isTrue();
        verifyNoInteractions(limiter);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(IP);
        return req;
    }

    private RateLimitRule rule(String name, String pattern, List<String> methods,
                               RateLimitRule.Dimension dimension, int permits) {
        RateLimitRule rule = new RateLimitRule();
        rule.setName(name);
        rule.setPatterns(List.of(pattern));
        rule.setMethods(methods);
        rule.setDimension(dimension);
        rule.setPermits(permits);
        rule.setWindowSeconds(1);
        return rule;
    }

    /** 造一个只带 subject 的 Claims：UserContext 用 subject 当 userId。 */
    private Claims claimsOf(String userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId);
        return claims;
    }
}
