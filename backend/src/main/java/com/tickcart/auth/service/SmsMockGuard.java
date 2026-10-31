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
 * <p>Mock 模式下验证码会随 {@code /api/auth/sms/send} 的响应回显（{@code data.devCode}），
 * 等于把码直接交给调用方：任何人拿到它就能登录任意账号、重置任意账号的密码。
 * 这种配置一旦被带上生产，前面所有频控都白做 —— 攻击者第一次就猜中，根本不需要爆破。</p>
 *
 * <p>所以这里把「静默裸奔」变成「起不来」：显式激活 prod/production profile 且仍开着
 * Mock 时，直接让应用启动失败。</p>
 *
 * <p><b>本项目不接真实短信网关</b>，所以这条守卫的实际含义是「本应用不能以 prod profile
 * 启动」。这不是缺陷：演示/开发环境不激活 profile 即可正常跑，而一旦有人打上 prod 标签，
 * 就说明这套代码被当成真生产用了 —— 那时必须先把网关接上。</p>
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
                            + "验证码会随响应回显，任何人可登录/重置任意账号。"
                            + "本项目未接真实短信网关，请勿以 prod/production profile 部署。");
        }
        log.warn("短信为 Mock 模式，验证码会随 /api/auth/sms/send 响应回显（仅限开发环境，禁止用于生产）");
    }
}
