package com.tickcart.auth.service;

import com.tickcart.common.api.ResultCode;
import com.tickcart.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 短信服务（Mock 实现）。
 *
 * <p><b>本项目不接真实短信网关，Mock 就是最终形态</b>：{@code tickcart.sms.mock-enabled=true}
 * 时只把验证码打到日志、并由 {@code AuthService} 随响应回显给前端
 * （见 {@code SmsCodeSendResult.devCode}），前后端联调与演示都靠它。</p>
 *
 * <p><b>验证码一律随机生成，不提供「固定码」配置。</b>早期版本在 Mock 下返回配置里的明文，
 * 任何人看到配置就能登录/重置任意账号 —— 那是安全漏洞，不是联调便利。要固定请改本类，
 * 不要把它做成配置项。</p>
 */
@Component
@ConfigurationProperties(prefix = "tickcart.sms")
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 6 位码取 100000~999999：首位非 0，避免前端/用户把 "012345" 当成 5 位。 */
    private static final int CODE_MIN = 100_000;
    private static final int CODE_SPAN = 900_000;

    /** 是否启用 Mock（不真实发送，验证码回显给前端）。 */
    private boolean mockEnabled = true;

    /** 验证码有效期（秒）。 */
    private int codeExpireSeconds = 300;

    /**
     * 生成随机验证码（6 位纯数字）。
     *
     * <p>与是否 Mock 无关：Mock 只决定「发不发真短信、回不回显」，不决定码本身。</p>
     */
    public String generateCode() {
        return String.valueOf(CODE_MIN + RANDOM.nextInt(CODE_SPAN));
    }

    /**
     * 发送验证码。
     *
     * <p>Mock 模式下仅打日志，不真实发送；验证码由 {@code AuthService} 回显给前端。</p>
     *
     * <p>{@code mock-enabled=false} 时直接抛异常是<b>刻意的 fail-loud</b>，不是待修的缺陷：
     * 没有网关却静默「发送成功」，会让人以为短信真的发出去了，排障时更难查。
     * 真要接网关，请在本方法里替换实现，同时保留这段兜底。</p>
     */
    public void send(String phone, String code) {
        if (!mockEnabled) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "短信网关未接入，请开启 tickcart.sms.mock-enabled");
        }
        log.info("[Mock 短信] 向 {} 发送验证码：{}", phone, code);
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public int getCodeExpireSeconds() {
        return codeExpireSeconds;
    }

    public void setCodeExpireSeconds(int codeExpireSeconds) {
        this.codeExpireSeconds = codeExpireSeconds;
    }
}
