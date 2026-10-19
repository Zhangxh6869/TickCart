package com.tickcart.common.exception;

import com.tickcart.common.api.ResultCode;

/**
 * 业务异常。
 *
 * <p>用于表达可预期的业务失败（如"用户名已存在""库存不足"），由
 * {@code GlobalExceptionHandler} 统一转成对应 HTTP 状态与 {@code R} 结构，
 * 不打印堆栈、不视为系统故障。</p>
 */
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }

    // ---- 常用工厂方法，减少调用侧样板代码 ----

    public static BizException badRequest(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ResultCode.NOT_FOUND, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ResultCode.CONFLICT, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(ResultCode.UNAUTHORIZED, message);
    }

    /** 503：依赖的中间件/预热数据暂不可用，稍后重试可恢复。 */
    public static BizException serviceUnavailable(String message) {
        return new BizException(ResultCode.SERVICE_UNAVAILABLE, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ResultCode.FORBIDDEN, message);
    }
}
