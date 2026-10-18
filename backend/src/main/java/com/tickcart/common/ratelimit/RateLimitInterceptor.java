package com.tickcart.common.ratelimit;

import com.tickcart.common.exception.RateLimitedException;
import com.tickcart.common.security.ClientIpResolver;
import com.tickcart.common.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 接口限流拦截器：在 Controller 之前按配置的规则放行或拒绝。
 *
 * <p><b>为什么是 Interceptor 而不是 Filter</b>：Filter 在 Spring Security 之前执行，
 * 那时 {@link UserContext} 还没被填充，按用户限流只能自己解析 JWT；Interceptor 在
 * Security 之后、Controller 之前，用户与 IP 都拿得到。</p>
 *
 * <p><b>为什么不是 AOP 注解</b>：项目没有 {@code spring-boot-starter-aop} 依赖，
 * 为一个限流引切面框架不划算；而且阈值应当可配置（改 yml 而非改代码）。</p>
 *
 * <p>这里抛出的 {@link RateLimitedException} 会走 {@code DispatcherServlet} 的异常解析链，
 * 由 {@code GlobalExceptionHandler} 统一转成 429 + {@code retryAfter}，不需要额外处理。</p>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties properties;
    private final RedisRateLimiter rateLimiter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitInterceptor(RateLimitProperties properties, RedisRateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled()) {
            return true;
        }
        RateLimitRule rule = matchRule(request);
        if (rule == null) {
            return true;
        }
        String subject = resolveSubject(request, rule.getDimension());
        if (subject == null) {
            // 取不到 IP 也取不到用户：宁可放行，也不要把匿名流量全挤进同一个桶
            return true;
        }

        long waitMs = rateLimiter.tryAcquire(
                rule.getName() + ":" + subject, rule.getPermits(), rule.getWindowSeconds());
        if (waitMs <= 0) {
            return true;
        }
        // 毫秒向上取整成秒：RateLimitedException 的文案是「请 X 秒后重试」
        throw new RateLimitedException((waitMs + 999) / 1000);
    }

    /**
     * 选出命中的规则：路径匹配 + 方法匹配，多条命中时取<b>路径最长</b>的那条。
     *
     * <p>「最长优先」而不是叠加计数：叠加会让一个请求同时消耗两个桶，
     * 阈值含义变得难以解释；最具体优先是网关的通行做法。</p>
     */
    private RateLimitRule matchRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        RateLimitRule best = null;
        int bestPatternLength = -1;

        for (RateLimitRule rule : properties.getRules()) {
            if (!methodAllowed(rule.getMethods(), method)) {
                continue;
            }
            for (String pattern : rule.getPatterns()) {
                if (pattern != null && pathMatcher.match(pattern, path)
                        && pattern.length() > bestPatternLength) {
                    best = rule;
                    bestPatternLength = pattern.length();
                }
            }
        }
        return best;
    }

    private boolean methodAllowed(List<String> methods, String method) {
        if (methods == null || methods.isEmpty()) {
            return true;
        }
        return methods.stream().anyMatch(m -> m != null && m.equalsIgnoreCase(method));
    }

    /**
     * 解析限流主体，形如 {@code user:2} 或 {@code ip:1.2.3.4}。
     *
     * <p>声明 {@code USER} 维度但请求未登录时<b>回退到 IP</b>：
     * 否则所有匿名请求会共用「空 userId」这一个桶，互相误伤。</p>
     */
    private String resolveSubject(HttpServletRequest request, RateLimitRule.Dimension dimension) {
        if (dimension == RateLimitRule.Dimension.USER) {
            Long userId = UserContext.getUserId();
            if (userId != null) {
                return "user:" + userId;
            }
        }
        String ip = ClientIpResolver.resolve(request);
        return ip == null || ip.isBlank() ? null : "ip:" + ip;
    }
}
