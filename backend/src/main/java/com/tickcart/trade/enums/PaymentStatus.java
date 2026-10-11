package com.tickcart.trade.enums;

import com.tickcart.common.exception.BizException;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付流水状态。
 *
 * <pre>
 *   0 待支付 ──渠道回调成功──▶ 1 支付成功 ──▶ 推进订单到「待发货」
 *      │
 *      ├──渠道回调失败──▶ 2 支付失败
 *      └──用户取消支付──▶ 3 已关闭
 * </pre>
 *
 * <p>「支付失败」与「已关闭」都**不改变订单状态**：订单仍是待付款，用户可以再次发起支付
 * （每次发起落一条新流水），直到 15 分钟支付时限到达被超时关单。</p>
 *
 * <p>1 / 2 / 3 都是终态：渠道通知可能重复到达，落库时用「仅待支付可变更」来保证幂等。</p>
 */
public enum PaymentStatus {

    /** 待支付：已创建支付单，等待渠道通知结果 */
    PENDING(0, "待支付"),

    /** 支付成功：订单推进到待发货 */
    SUCCESS(1, "支付成功"),

    /** 支付失败：订单保持待付款，可重新发起 */
    FAILED(2, "支付失败"),

    /** 已关闭：用户主动取消支付，订单保持待付款 */
    CLOSED(3, "已关闭");

    private final int code;
    private final String label;

    /** 状态码 → 枚举（key 是 Integer，故用 HashMap 而非 EnumMap） */
    private static final Map<Integer, PaymentStatus> BY_CODE = new HashMap<>();

    static {
        for (PaymentStatus status : values()) {
            BY_CODE.put(status.code, status);
        }
    }

    PaymentStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 是否为终态（不再接受渠道通知变更）。 */
    public boolean isFinal() {
        return this != PENDING;
    }

    /** 按状态码取枚举；未知状态码抛业务异常。 */
    public static PaymentStatus of(int code) {
        PaymentStatus status = BY_CODE.get(code);
        if (status == null) {
            throw BizException.badRequest("未知的支付状态：" + code);
        }
        return status;
    }
}
