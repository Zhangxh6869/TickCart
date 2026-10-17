package com.tickcart.common.exception;

import com.tickcart.common.api.R;
import com.tickcart.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>把各类异常统一收敛为 {@link R} 结构，并按语义映射 HTTP 状态码：
 * 参数校验 → 400，业务异常按 {@link ResultCode} 前缀映射，未知异常 → 500 并记录堆栈。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可预期，不打堆栈。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBizException(BizException ex, HttpServletRequest request) {
        ResultCode rc = ex.getResultCode();
        log.warn("业务异常 [{}] {} -> {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(httpStatusOf(rc)).body(R.fail(rc, ex.getMessage()));
    }

    /**
     * 需要人机验证：401 + 验证挑战。
     *
     * <p>必须排在 {@link #handleBizException} 之前被匹配 —— Spring 按异常类型的
     * 继承距离选择最具体的处理器，故子类会自动胜出，与声明顺序无关。</p>
     */
    @ExceptionHandler(VerifyRequiredException.class)
    public ResponseEntity<R<Object>> handleVerifyRequired(VerifyRequiredException ex,
                                                          HttpServletRequest request) {
        log.warn("需要安全验证 [{}] {} -> {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(ResultCode.NEED_VERIFY, ex.getMessage(), ex.getChallenge()));
    }

    /** 频控拦截：429 + 剩余等待秒数。 */
    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<R<Object>> handleRateLimited(RateLimitedException ex,
                                                       HttpServletRequest request) {
        log.warn("请求被限流 [{}] {} -> 还需 {} 秒", request.getMethod(), request.getRequestURI(),
                ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(R.fail(ResultCode.TOO_MANY_REQUESTS, ex.getMessage(),
                        Map.of("retryAfter", ex.getRetryAfterSeconds())));
    }

    /** @Valid 校验失败：请求体参数不合法。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return badRequest(message);
    }

    /** 表单/查询参数绑定失败。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Void>> handleBind(BindException ex) {
        String message = ex.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return badRequest(message);
    }

    /** 缺少必填请求参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return badRequest("缺少必填参数：" + ex.getParameterName());
    }

    /** 请求方法不支持。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(R.fail(ResultCode.METHOD_NOT_ALLOWED));
    }

    /** 上传文件超过 multipart 硬上限（业务侧还有更小的图片上限，见 {@code tickcart.upload.max-size-mb}）。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return badRequest("上传文件过大，请压缩后重试");
    }

    /** 认证失败（未登录/令牌无效）。 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<R<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(ResultCode.UNAUTHORIZED, "未登录或登录已过期"));
    }

    /** 授权失败（已登录但无权限）。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(ResultCode.FORBIDDEN));
    }

    /** 兜底：未知异常视为系统故障，记录完整堆栈便于排查。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 [{}] {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(ResultCode.INTERNAL_ERROR));
    }

    private ResponseEntity<R<Void>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ResultCode.BAD_REQUEST, message));
    }

    /** 依据 ResultCode 的 4xxxx/5xxxx 前缀推导 HTTP 状态。 */
    private HttpStatus httpStatusOf(ResultCode rc) {
        return switch (rc) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED, NEED_VERIFY -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
