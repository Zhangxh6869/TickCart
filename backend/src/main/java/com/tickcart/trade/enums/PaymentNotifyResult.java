package com.tickcart.trade.enums;

import com.tickcart.common.exception.BizException;

/**
 * 支付渠道通知的结果。
 *
 * <p>两种取值语义并存，各管一段：</p>
 * <ul>
 *   <li>{@code code} —— 模拟渠道接口请求体里的取值（success / fail / cancel）；</li>
 *   <li>{@code target} —— 落库时对应的支付流水状态（见 {@link PaymentStatus}）。</li>
 * </ul>
 *
 * <p>真实渠道只回调「成功」，失败与取消由商户主动查单得到；模拟环境为了让前端被迫处理
 * 「结果不确定」的分支，把三种结果都做成可主动触发的。</p>
 */
public enum PaymentNotifyResult {

    /** 支付成功：推进订单到待发货 */
    SUCCESS("success", PaymentStatus.SUCCESS),

    /** 支付失败：订单保持待付款，可重新发起 */
    FAILED("fail", PaymentStatus.FAILED),

    /** 用户取消支付：订单保持待付款 */
    CANCELLED("cancel", PaymentStatus.CLOSED);

    /** 渠道请求体里的取值 */
    private final String code;

    /** 对应的支付流水状态 */
    private final PaymentStatus target;

    PaymentNotifyResult(String code, PaymentStatus target) {
        this.code = code;
        this.target = target;
    }

    public String getCode() {
        return code;
    }

    public PaymentStatus getTarget() {
        return target;
    }

    /** 按渠道请求体取值解析；未知取值抛业务异常（避免静默按成功处理）。 */
    public static PaymentNotifyResult of(String code) {
        for (PaymentNotifyResult result : values()) {
            if (result.code.equalsIgnoreCase(code)) {
                return result;
            }
        }
        throw BizException.badRequest("未知的支付结果：" + code);
    }
}
