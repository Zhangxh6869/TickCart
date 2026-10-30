package com.tickcart.trade.enums;

import com.tickcart.common.exception.BizException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态与状态机。
 *
 * <p>流转规则集中在此处声明，Service 层只调用 {@link #canTransitionTo} 校验，
 * 避免非法跳转（如待发货直接完成）散落在各处业务代码里。</p>
 *
 * <pre>
 *   0 待付款 ──支付──▶ 1 待发货 ──发货──▶ 2 待收货 ──确认收货──▶ 3 已完成
 *      │                 │                  │                    │
 *      │                 └──────申请退款─────┴────────────────────┘
 *      │                          │
 *      │                          ▼
 *      │                     6 退款中 ──退款完成──▶ 7 已退款
 *      │                          │
 *      │                          └──拒绝/撤销──▶ 回到 1 / 2 / 3
 *      │
 *      ├──用户取消──▶ 4 已取消
 *      └──超时未付──▶ 5 已超时关闭
 * </pre>
 *
 * <p><b>关于 6 / 7 与「回退边」</b>：退款中是个中转态，审核结果有三种 ——
 * 完成则进 7（终态），拒绝或用户撤销则**回到申请前的状态**。因此
 * {@code REFUNDING} 的出边里保留了 1 / 2 / 3，它们是「回退边」而非正常流转。
 * 回退目标不写死，由 {@code refund_record.origin_status} 决定。</p>
 *
 * <p>★ <b>退款入口</b>：1 待发货（未发货退款）、2 待收货（仅退款）、3 已完成（退货退款）。
 * 因此 <b>「已完成」不是终态</b>（{@link #isFinal()} 为 false）——
 * 售后是它唯一还能走的出口。</p>
 */
public enum OrderStatus {

    /** 待付款：下单后的初始状态，占用库存 */
    PENDING_PAYMENT(0, "待付款"),

    /** 待发货：已支付，等待商家发货 */
    PENDING_SHIPMENT(1, "待发货"),

    /** 待收货：已发货 */
    PENDING_RECEIPT(2, "待收货"),

    /** 已完成：确认收货，终态 */
    COMPLETED(3, "已完成"),

    /** 已取消：用户主动取消，终态 */
    CANCELLED(4, "已取消"),

    /** 已超时关闭：待付款超时自动关闭，终态 */
    TIMEOUT_CLOSED(5, "已超时关闭"),

    /** 退款中：已提交退款申请，等待商家审核（中转态，不是终态） */
    REFUNDING(6, "退款中"),

    /** 已退款：商家同意并完成退款，终态 */
    REFUNDED(7, "已退款");

    private final int code;
    private final String label;

    /** 状态码 → 枚举（key 是 Integer，故用 HashMap 而非 EnumMap） */
    private static final Map<Integer, OrderStatus> BY_CODE = new HashMap<>();

    static {
        for (OrderStatus status : values()) {
            BY_CODE.put(status.code, status);
        }
    }

    /** 合法流转表：键为当前状态，值为允许的下一状态 */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(PENDING_PAYMENT, EnumSet.of(PENDING_SHIPMENT, CANCELLED, TIMEOUT_CLOSED));
        // 退款入口：待发货（P0 未发货退款）、待收货（P1 仅退款）、已完成（P2 退货退款）
        TRANSITIONS.put(PENDING_SHIPMENT, EnumSet.of(PENDING_RECEIPT, REFUNDING));
        TRANSITIONS.put(PENDING_RECEIPT, EnumSet.of(COMPLETED, REFUNDING));
        // ★ P2 起「已完成」不再是绝对终态：还能发起退货退款，所以 isFinal() 变 false。
        //   这不是妥协 —— 加了售后之后它确实可以流转。
        TRANSITIONS.put(COMPLETED, EnumSet.of(REFUNDING));
        TRANSITIONS.put(CANCELLED, Collections.emptySet());
        TRANSITIONS.put(TIMEOUT_CLOSED, Collections.emptySet());
        // 1 / 2 / 3 是「回退边」：拒绝退款或用户撤销时回到申请前的状态。
        // 回退目标由 refund_record.origin_status 决定。
        TRANSITIONS.put(REFUNDING, EnumSet.of(REFUNDED, PENDING_SHIPMENT,
                PENDING_RECEIPT, COMPLETED));
        TRANSITIONS.put(REFUNDED, Collections.emptySet());
    }

    OrderStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 按状态码取枚举；未知状态码抛业务异常。 */
    public static OrderStatus of(int code) {
        OrderStatus status = BY_CODE.get(code);
        if (status == null) {
            throw BizException.badRequest("未知的订单状态：" + code);
        }
        return status;
    }

    /** 是否允许流转到目标状态。 */
    public boolean canTransitionTo(OrderStatus target) {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    /**
     * 校验流转合法性，非法时抛业务异常。
     *
     * @param target 目标状态
     * @param action 动作描述，用于拼装提示（如「取消」）
     */
    public void assertTransitionTo(OrderStatus target, String action) {
        if (!canTransitionTo(target)) {
            throw BizException.conflict("订单当前为「" + label + "」，无法" + action);
        }
    }

    /** 是否为终态（不可再流转）。 */
    public boolean isFinal() {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).isEmpty();
    }
}
