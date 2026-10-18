package com.tickcart.common.ratelimit;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条限流规则，对应 {@code tickcart.rate-limit.rules[]} 的一项。
 *
 * <p>规则用配置而不是注解声明：阈值是运营参数，改完重启即生效，不必改代码
 * （AGENTS.md 4.3「禁止硬编码」）。代价是按路径匹配、不如注解精确，
 * 所以额外提供 {@link #methods} 用于区分同一路径下的不同动作
 * （如 {@code /api/orders} 的 GET 列表与 POST 创建）。</p>
 */
public class RateLimitRule {

    /** 限流维度 */
    public enum Dimension {
        /** 按客户端 IP：匿名可访问的接口用它 */
        IP,
        /** 按登录用户：需要登录的接口用它；未登录时回退 IP */
        USER
    }

    /** 规则名，同时用作 Redis key 前缀，便于排查 */
    private String name;

    /** Ant 风格路径，如 {@code /api/search/**} */
    private List<String> patterns = new ArrayList<>();

    /** 限定 HTTP 方法；为空表示不限方法 */
    private List<String> methods = new ArrayList<>();

    private Dimension dimension = Dimension.IP;

    /** 令牌桶容量 = 一个窗口内允许的次数 */
    private int permits = 10;

    /** 窗口长度（秒），令牌按 permits/windowSeconds 的速率补充 */
    private int windowSeconds = 1;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<String> patterns) {
        this.patterns = patterns;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public void setDimension(Dimension dimension) {
        this.dimension = dimension;
    }

    public int getPermits() {
        return permits;
    }

    public void setPermits(int permits) {
        this.permits = permits;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}
