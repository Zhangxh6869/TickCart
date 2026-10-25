package com.tickcart.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量发货结果。
 *
 * <p><b>★ 允许部分成功</b>，与商品批量接口「整批拒绝」的语义刻意相反：发货对应的是
 * 已经发生的物理动作（货打包好了），不能因为其中某一单的状态被他人改过，
 * 就把其余几十单的「已发货」一起回滚。失败项会带原因，由前端列出来让管理员单独处理。</p>
 */
@Schema(description = "批量发货结果")
public class BatchShipResultVO {

    @Schema(description = "发货成功的订单号")
    private List<String> success = new ArrayList<>();

    @Schema(description = "发货失败的订单号与原因")
    private List<Failure> failed = new ArrayList<>();

    /** 单条失败原因。 */
    @Schema(description = "发货失败项")
    public static class Failure {

        @Schema(description = "订单号")
        private String orderNo;

        @Schema(description = "失败原因")
        private String reason;

        public Failure() {
        }

        public Failure(String orderNo, String reason) {
            this.orderNo = orderNo;
            this.reason = reason;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public List<String> getSuccess() {
        return success;
    }

    public void setSuccess(List<String> success) {
        this.success = success;
    }

    public List<Failure> getFailed() {
        return failed;
    }

    public void setFailed(List<Failure> failed) {
        this.failed = failed;
    }
}
