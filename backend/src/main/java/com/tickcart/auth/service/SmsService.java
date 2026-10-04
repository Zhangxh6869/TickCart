package com.tickcart.auth.service;

import com.tickcart.common.exception.BizException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 短信服务（阶段1 Mock 实现）。
 *
 * <p>开发环境不接真实短信网关：配置 {@code tickcart.sms.mock-enabled=true} 时
 * 固定返回 {@code tickcart.sms.mock-code}（默认 123456），便于前后端联调；
 * 生成与发送逻辑集中在此，后续替换为真实网关只需改本类实现。</p>
 */
@Component
@ConfigurationProperties(prefix = "tickcart.sms")
public class SmsService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;

    /** 是否启用 Mock（不真实发送）。 */
    private boolean mockEnabled = true;

    /** Mock 模式下的固定验证码。 */
    private String mockCode = "123456";

    /** 验证码有效期（秒）。 */
    private int codeExpireSeconds = 300;

    /**
     * 生成验证码（Mock 模式返回固定码）。
     */
    public String generateCode() {
        if (mockEnabled) {
            return mockCode;
        }
        return String.format("%06d", RANDOM.nextInt(CODE_BOUND));
    }

    /**
     * 发送验证码。
     *
     * <p>阶段1 为 Mock：真实环境应在次处调用短信网关。为保证联调便利，
     * 开发环境通过日志输出验证码。</p>
     */
    public void send(String phone, String code) {
        if (!mockEnabled) {
            throw new BizException(com.tickcart.common.api.ResultCode.INTERNAL_ERROR,
                    "短信网关未接入，请开启 tickcart.sms.mock-enabled");
        }
        // 日志输出便于本地联调，不涉及真实发送
        org.slf4j.LoggerFactory.getLogger(SmsService.class)
                .info("[Mock 短信] 向 {} 发送验证码：{}", phone, code);
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public String getMockCode() {
        return mockCode;
    }

    public void setMockCode(String mockCode) {
        this.mockCode = mockCode;
    }

    public int getCodeExpireSeconds() {
        return codeExpireSeconds;
    }

    public void setCodeExpireSeconds(int codeExpireSeconds) {
        this.codeExpireSeconds = codeExpireSeconds;
    }
}
