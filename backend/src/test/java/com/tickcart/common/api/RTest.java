package com.tickcart.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link R} 统一响应体单元测试。
 */
@DisplayName("R 统一响应体")
class RTest {

    @Test
    @DisplayName("ok() 返回成功码且 data 为空")
    void okWithoutData() {
        R<Void> r = R.ok();
        assertThat(r.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
        assertThat(r.getMessage()).isEqualTo("success");
        assertThat(r.getData()).isNull();
    }

    @Test
    @DisplayName("ok(data) 携带业务数据")
    void okWithData() {
        R<String> r = R.ok("hello");
        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    @DisplayName("fail(ResultCode) 使用枚举默认提示")
    void failWithResultCode() {
        R<Void> r = R.fail(ResultCode.NOT_FOUND);
        assertThat(r.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(r.getMessage()).isEqualTo(ResultCode.NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("fail(ResultCode, message) 覆盖默认提示")
    void failWithCustomMessage() {
        R<Void> r = R.fail(ResultCode.BAD_REQUEST, "用户名不能为空");
        assertThat(r.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(r.getMessage()).isEqualTo("用户名不能为空");
    }
}
