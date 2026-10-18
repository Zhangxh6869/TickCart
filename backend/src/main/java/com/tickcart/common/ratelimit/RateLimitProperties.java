package com.tickcart.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口限流配置，对应 {@code tickcart.rate-limit.*}。
 *
 * <p>没命中任何规则的接口不限流（如 {@code /api/health}、{@code /api/cart/**}、
 * {@code /api/admin/**}）—— 白名单是「默认不限」，只有显式配置的路径才加闸门。</p>
 */
@Component
@ConfigurationProperties(prefix = "tickcart.rate-limit")
public class RateLimitProperties {

    /** 总开关：关掉后所有接口不限流（压测/并发验证时需要关）。 */
    private boolean enabled = true;

    private List<RateLimitRule> rules = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<RateLimitRule> getRules() {
        return rules;
    }

    public void setRules(List<RateLimitRule> rules) {
        this.rules = rules;
    }
}
