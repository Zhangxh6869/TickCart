package com.tickcart.auth.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SmsCodeSendResult} 序列化护栏。
 *
 * <p>「关闭 Mock 时不下发验证码」这件事，光在 Service 里把值置 null 还不够 ——
 * 一旦有人去掉 {@code @JsonInclude}，生产响应里就会出现 {@code "devCode": null}，
 * 字段名本身就是提示。这里直接从 JSON 文本层面断言。</p>
 */
@DisplayName("发送验证码结果序列化")
class SmsCodeSendResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Mock 模式：devCode 与有效期都出现在响应里")
    void serializesDevCodeWhenPresent() throws Exception {
        String json = mapper.writeValueAsString(new SmsCodeSendResult(300, "483920"));

        assertThat(json).contains("\"expireSeconds\":300");
        assertThat(json).contains("\"devCode\":\"483920\"");
    }

    @Test
    @DisplayName("关闭 Mock：devCode 为 null 时字段整个消失，连字段名都不留")
    void omitsDevCodeWhenNull() throws Exception {
        String json = mapper.writeValueAsString(new SmsCodeSendResult(300, null));

        assertThat(json).contains("\"expireSeconds\":300");
        assertThat(json).doesNotContain("devCode");
    }
}
