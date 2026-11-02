package com.tickcart.common.exception;

import com.tickcart.common.api.R;
import com.tickcart.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GlobalExceptionHandler} 单元测试（不依赖 Spring 上下文）。
 */
@DisplayName("全局异常处理器")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("BizException 按 ResultCode 映射 HTTP 状态")
    void bizExceptionMapsStatus() {
        ResponseEntity<R<Void>> resp =
                handler.handleBizException(BizException.notFound("商品不存在"), mockRequest());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(resp.getBody().getMessage()).isEqualTo("商品不存在");
    }

    @Test
    @DisplayName("BizException.conflict 映射 409")
    void conflictMapsTo409() {
        ResponseEntity<R<Void>> resp =
                handler.handleBizException(BizException.conflict("用户名已存在"), mockRequest());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.CONFLICT.getCode());
    }

    @Test
    @DisplayName("未知异常兜底 500 且不泄漏内部信息")
    void unknownMapsTo500() {
        ResponseEntity<R<Void>> resp =
                handler.handleUnknown(new IllegalStateException("boom"), mockRequest());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.INTERNAL_ERROR.getCode());
        assertThat(resp.getBody().getMessage()).isEqualTo(ResultCode.INTERNAL_ERROR.getMessage());
    }

    /**
     * 路由不存在必须落成 404，不能掉进 {@code Exception} 兜底变成 500。
     *
     * <p>Spring 6.1 在「路径没有对应 handler」时抛 {@link NoResourceFoundException}，
     * 它本身就带 404 语义。若不单独声明，会被兜底分支当成未知故障：既返回 50000
     * 误导前端（接口拼错被当成服务挂了），又打一条 ERROR + 堆栈污染日志
     * （真故障来了反而被噪音淹掉）。</p>
     */
    @Test
    @DisplayName("路由不存在映射 404，不掉进 500 兜底")
    void noResourceFoundMapsTo404() {
        NoResourceFoundException ex =
                new NoResourceFoundException(HttpMethod.GET, "/api/search");

        ResponseEntity<R<Void>> resp = handler.handleNoResourceFound(ex, mockRequest());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        // 文案用统一文案，不回显请求路径，避免把内部路由结构暴露给调用方
        assertThat(resp.getBody().getMessage()).isEqualTo(ResultCode.NOT_FOUND.getMessage());
    }

    /** 仅提供日志所需的 method/uri，其余方法用 Mockito 默认实现。 */
    private HttpServletRequest mockRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");
        return request;
    }
}
