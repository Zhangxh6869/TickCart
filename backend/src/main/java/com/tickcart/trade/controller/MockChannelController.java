package com.tickcart.trade.controller;

import com.tickcart.common.api.R;
import com.tickcart.trade.dto.MockChannelNotifyRequest;
import com.tickcart.trade.enums.PaymentNotifyResult;
import com.tickcart.trade.service.MockChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟支付渠道接口（扮演第三方渠道服务端，仅用于本地联调与演示）。
 *
 * <p>与 {@code /api/payments/callback/**} 的区别：那个是**商城侧**接收渠道通知的入口
 * （匿名 + 真实场景靠验签），这个则是**渠道侧**受理用户付款的入口，需要登录。
 * 真实接入后本控制器整体删除。</p>
 *
 * <p>接口返回成功只代表渠道已受理，**订单此时尚未变更**：结果要经 MQ 延迟消息异步生效，
 * 前端必须回收银台轮询支付状态。</p>
 */
@Tag(name = "模拟支付渠道", description = "扮演第三方渠道服务端，模拟延迟回调")
@RestController
@RequestMapping("/api/mock-channel")
public class MockChannelController {

    private final MockChannelService mockChannelService;

    public MockChannelController(MockChannelService mockChannelService) {
        this.mockChannelService = mockChannelService;
    }

    @Operation(summary = "提交支付结果", description = "success 成功 / fail 失败 / cancel 用户取消；延迟生效")
    @PostMapping("/{payNo}/notify")
    public R<Void> notify(@PathVariable String payNo,
                          @Valid @RequestBody MockChannelNotifyRequest req) {
        mockChannelService.submitResult(payNo, PaymentNotifyResult.of(req.getResult()));
        return R.ok();
    }
}
