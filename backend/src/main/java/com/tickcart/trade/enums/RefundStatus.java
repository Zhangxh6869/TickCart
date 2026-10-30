package com.tickcart.trade.enums;

import com.tickcart.common.exception.BizException;

import java.util.HashMap;
import java.util.Map;

/**
 * 退款单状态。
 *
 * <pre>
 *   未发货退款 / 仅退款（type=1/2）：
 *   0 待审核 ──商家同意──▶ 1 已退款（终态，订单置「已退款」）
 *      ├──商家拒绝──▶ 2 已拒绝（终态，订单回退 origin_status）
 *      └──用户撤销──▶ 3 已撤销（终态，订单回退 origin_status）
 *
 *   退货退款（type=3）：同意之后还有一段物理流程
 *   0 待审核 ──商家同意──▶ 4 待寄回 ──用户填单号──▶ 5 待收货 ──商家验收──▶ 1 已退款
 *                              ├──用户撤销──▶ 3 已撤销
 *                              └──商家关闭──▶ 2 已拒绝
 * </pre>
 *
 * <p><b>1 / 2 / 3 是终态，4 / 5 不是</b>：待寄回与待收货都属于「退款进行中」，
 * 所以 {@code active_key} 仍占着订单号，同一订单不能再次申请。
 * 审核动作可能被重复提交（管理员连点、网络重试），落库时用条件 UPDATE 保证幂等。</p>
 */
public enum RefundStatus {

    /** 待审核：用户已提交申请，等待商家处理 */
    PENDING_AUDIT(0, "待审核"),

    /** 已退款：款项已退回（退货退款为验收通过后） */
    REFUNDED(1, "已退款"),

    /** 已拒绝：商家驳回，订单回到申请前的状态 */
    REJECTED(2, "已拒绝"),

    /** 已撤销：用户在流程结束前主动撤回 */
    CANCELLED(3, "已撤销"),

    /** 待寄回：商家已同意退货，等用户寄出（仅 type=3） */
    WAIT_RETURN(4, "待寄回"),

    /** 待收货：用户已填物流单号，等商家验收（仅 type=3） */
    WAIT_RECEIVE(5, "待收货");

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

    /**
     * 是否为终态（不再接受任何动作变更）。
     *
     * <p>★ 待寄回 / 待收货**不是**终态：退货流程还在走，{@code active_key} 仍占着订单号。
     * 把它们当终态会导致「同意退货后同一订单能再申请一次退款」。</p>
     */
    public boolean isFinal() {
        return this != PENDING_AUDIT && this != WAIT_RETURN && this != WAIT_RECEIVE;
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
