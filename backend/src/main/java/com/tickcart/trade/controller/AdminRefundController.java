package com.tickcart.trade.controller;

import com.tickcart.common.api.R;
import com.tickcart.trade.dto.RefundRejectRequest;
import com.tickcart.trade.service.RefundService;
import com.tickcart.trade.vo.RefundVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台退款审核接口（需 ROLE_ADMIN）。
 *
 * <p>路径前缀 {@code /api/admin/**}，由 {@code SecurityConfig} 统一要求管理员角色。</p>
 *
 * <p><b>P0 刻意不提供「退款单列表」接口</b>：退款中的订单在订单列表里就是
 * {@code status=6}，后台直接用它当待办队列，不必再维护一套平行的列表 ——
 * 两套列表的筛选口径迟早会不一致。等 P1 出现「同一订单多次售后」时再拆。</p>
 */
@Tag(name = "退款(后台)", description = "退款单查询与审核")
@RestController
@RequestMapping("/api/admin/refunds")
public class AdminRefundController {

    private final RefundService refundService;

    public AdminRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @Operation(summary = "退款单详情", description = "按退款单号查询；审核抽屉通常从订单详情直接拿到，此接口供独立查询")
    @GetMapping("/{refundNo}")
    public R<RefundVO> detail(@PathVariable String refundNo) {
        return R.ok(refundService.findByRefundNo(refundNo));
    }

    @Operation(summary = "同意退款",
            description = "置「已退款」→ 回补库存 → 回退销量。重复提交按幂等处理，不会重复回补库存")
    @PostMapping("/{refundNo}/approve")
    public R<Void> approve(@PathVariable String refundNo) {
        refundService.approve(refundNo);
        return R.ok();
    }

    @Operation(summary = "拒绝退款", description = "拒绝原因必填，会展示给用户；订单回退到申请前的状态")
    @PostMapping("/{refundNo}/reject")
    public R<Void> reject(@PathVariable String refundNo,
                          @Valid @RequestBody RefundRejectRequest req) {
        refundService.reject(refundNo, req.getAuditRemark());
        return R.ok();
    }
}
