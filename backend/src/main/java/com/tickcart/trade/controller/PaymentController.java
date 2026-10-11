package com.tickcart.trade.controller;

import com.tickcart.common.api.R;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.UserContext;
import com.tickcart.trade.dto.PaymentRequest;
import com.tickcart.trade.service.PaymentService;
import com.tickcart.trade.vo.PaymentStatusVO;
import com.tickcart.trade.vo.PaymentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口（需登录）。
 *
 * <p>收银台的完整链路分三段，本控制器只覆盖商城侧的两端：</p>
 * <ol>
 *   <li>{@code POST /api/payments/{orderNo}} —— 发起支付，落一条「待支付」流水，**不推进订单**；</li>
 *   <li>用户在渠道侧付款（模拟渠道见 {@code MockChannelController}），渠道延迟回调；</li>
 *   <li>{@code GET /api/payments/{orderNo}/status} —— 前端轮询，直到订单变为已支付或流水落终态。</li>
 * </ol>
 *
 * <p>{@code /api/payments/callback/{payNo}} 为第三方渠道的异步通知入口，当前无鉴权，
 * 仅用于本地联调与回归验证。</p>
 */
@Tag(name = "支付", description = "发起支付、查询支付状态与支付结果回调")
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "发起支付", description = "仅待付款订单可支付；返回待支付流水，订单需等渠道回调后才变更")
    @PostMapping("/{orderNo}")
    public R<PaymentVO> pay(@PathVariable String orderNo, @Valid @RequestBody PaymentRequest req) {
        return R.ok(paymentService.createPayment(currentUserId(), orderNo, req.getChannel()));
    }

    @Operation(summary = "查询支付状态", description = "供收银台轮询：同时返回订单状态与最新一笔支付流水状态")
    @GetMapping("/{orderNo}/status")
    public R<PaymentStatusVO> status(@PathVariable String orderNo) {
        return R.ok(paymentService.queryStatus(currentUserId(), orderNo));
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
