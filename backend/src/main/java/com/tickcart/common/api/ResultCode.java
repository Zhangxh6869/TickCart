package com.tickcart.common.api;

/**
 * 全局业务状态码。
 *
 * <p>约定：0 成功；4xxxx 客户端语义（对应 HTTP 400/401/403/404/409/429）；
 * 5xxxx 服务端语义。code 与 HTTP 状态的映射见 {@code GlobalExceptionHandler}。</p>
 */
public enum ResultCode {

    SUCCESS(0, "success"),

    // ---- 客户端错误 ----
    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "没有权限访问该资源"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),
    CONFLICT(40900, "资源冲突"),
    TOO_MANY_REQUESTS(42900, "请求过于频繁，请稍后再试"),

    // ---- 服务端错误 ----
    INTERNAL_ERROR(50000, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
