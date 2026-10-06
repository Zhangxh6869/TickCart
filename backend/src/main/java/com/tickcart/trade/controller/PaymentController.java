package com.tickcart.trade.controller;

import com.tickcart.common.api.R;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.UserContext;
import com.tickcart.trade.dto.PaymentRequest;
import com.tickcart.trade.service.PaymentService;
import com.tickcart.trade.vo.PaymentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口（需登录）。
 *
 * <p>{@code /api/payments/{orderNo}} 为用户主动发起支付；{@code /api/payments/callback/{payNo}}
 * 模拟第三方渠道的异步通知入口，当前无鉴权，仅用于本地联调与回归验证。</p>
 */
@Tag(name = "支付", description = "模拟支付发起与支付结果回调")
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "发起支付", description = "仅待付款订单可支付，成功后订单进入待发货")
    @PostMapping("/{orderNo}")
    public R<PaymentVO> pay(@PathVariable String orderNo, @Valid @RequestBody PaymentRequest req) {
        return R.ok(paymentService.pay(currentUserId(), orderNo, req.getChannel()));
    }

    @Operation(summary = "支付回调（模拟渠道通知）", description = "幂等：重复通知不会重复推进订单")
    @PostMapping("/callback/{payNo}")
    public R<Void> callback(@PathVariable String payNo,
                            @RequestParam(defaultValue = "true") boolean success) {
        paymentService.handleCallback(payNo, success);
        return R.ok();
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        return userId;
    }
}
