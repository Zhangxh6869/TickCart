package com.tickcart.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 发送短信验证码的结果。
 *
 * <p>{@code devCode} 只在 Mock 模式（{@code tickcart.sms.mock-enabled=true}）下才有值，
 * 让本地/联调环境不必翻后端日志就能拿到码。真实网关模式下恒为 {@code null}，
 * 并由类级 {@link JsonInclude} 保证该字段整个不出现在响应 JSON 里 —— 生产环境连字段名都看不到。</p>
 *
 * @param expireSeconds 验证码有效期（秒）
 * @param devCode       仅 Mock 模式回显的验证码，生产为 null
 */
@Schema(description = "发送短信验证码结果")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmsCodeSendResult(int expireSeconds, String devCode) {
}
