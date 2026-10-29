package com.tickcart.trade.enums;

import com.tickcart.common.exception.BizException;

import java.util.HashMap;
import java.util.Map;

/**
 * 退款单状态。
 *
 * <pre>
 *   0 待审核 ──商家同意──▶ 1 已退款（终态，订单置「已退款」并回补库存）
 *      │
 *      ├──商家拒绝──▶ 2 已拒绝（终态，订单回退 origin_status）
 *      └──用户撤销──▶ 3 已撤销（终态，订单回退 origin_status）
 * </pre>
 *
 * <p>与订单状态的关系：待审核对应订单的「退款中」，已退款对应订单的「已退款」；
 * 已拒绝 / 已撤销时订单回到申请前的状态（见 {@code refund_record.origin_status}）。</p>
 *
 * <p>1 / 2 / 3 都是终态：审核动作可能被重复提交（管理员连点、网络重试），
 * 落库时用「仅待审核可变更」的条件 UPDATE 保证幂等。</p>
 */
public enum RefundStatus {

    /** 待审核：用户已提交申请，等待商家处理 */
    PENDING_AUDIT(0, "待审核"),

    /** 已退款：商家同意，款项已退回，库存已回补 */
    REFUNDED(1, "已退款"),

    /** 已拒绝：商家驳回，订单回到申请前的状态 */
    REJECTED(2, "已拒绝"),

    /** 已撤销：用户在审核前主动撤回 */
    CANCELLED(3, "已撤销");

    private final int code;
    private final String label;

    /** 状态码 → 枚举（key 是 Integer，故用 HashMap 而非 EnumMap） */
    private static final Map<Integer, RefundStatus> BY_CODE = new HashMap<>();

    static {
        for (RefundStatus status : values()) {
            BY_CODE.put(status.code, status);
        }
    }

    RefundStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 是否为终态（不再接受审核动作变更）。 */
    public boolean isFinal() {
        return this != PENDING_AUDIT;
    }

    /** 按状态码取枚举；未知状态码抛业务异常。 */
    public static RefundStatus of(int code) {
        RefundStatus status = BY_CODE.get(code);
        if (status == null) {
            throw BizException.badRequest("未知的退款状态：" + code);
        }
        return status;
    }
}
