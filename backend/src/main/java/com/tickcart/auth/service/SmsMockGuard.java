package com.tickcart.auth.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 短信 Mock 模式的生产环境守卫。
 *
 * <p>Mock 模式下验证码是配置里写死的明文（默认 {@code 123456}），任何人只要看到配置
 * 就能登录任意账号、重置任意账号的密码。这种配置一旦被带上生产，前面所有频控都白做 ——
 * 攻击者第一次就猜中，根本不需要爆破。</p>
 *
 * <p>所以这里把「静默裸奔」变成「起不来」：显式激活 prod/production profile 且仍开着
 * Mock 时，直接让应用启动失败。</p>
 *
 * <p>未激活任何 profile 视为本地开发（项目目前没有 profile 文件），不拦 ——
 * 否则本地 `mvn spring-boot:run` 会直接起不来。</p>
 */
@Component
public class SmsMockGuard {

    private static final Logger log = LoggerFactory.getLogger(SmsMockGuard.class);

    private final SmsService smsService;
    private final Environment environment;

    public SmsMockGuard(SmsService smsService, Environment environment) {
        this.smsService = smsService;
        this.environment = environment;
    }

    @PostConstruct
    void assertNotMockInProduction() {
        if (!smsService.isMockEnabled()) {
            return;
        }
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
        if (production) {
            throw new IllegalStateException(
                    "生产环境禁止开启短信 Mock（tickcart.sms.mock-enabled=true）："
                            + "验证码为固定明文，任何人可登录/重置任意账号。"
                            + "请接入真实短信网关后关闭 Mock。");
        }
        log.warn("短信为 Mock 模式，验证码固定为 {}（仅限开发环境，禁止用于生产）",
                smsService.getMockCode());
    }
}
