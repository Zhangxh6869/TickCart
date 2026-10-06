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
 *      │
 *      ├──用户取消──▶ 4 已取消
 *      └──超时未付──▶ 5 已超时关闭
 * </pre>
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
    TIMEOUT_CLOSED(5, "已超时关闭");

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
        TRANSITIONS.put(PENDING_SHIPMENT, EnumSet.of(PENDING_RECEIPT));
        TRANSITIONS.put(PENDING_RECEIPT, EnumSet.of(COMPLETED));
        TRANSITIONS.put(COMPLETED, Collections.emptySet());
        TRANSITIONS.put(CANCELLED, Collections.emptySet());
        TRANSITIONS.put(TIMEOUT_CLOSED, Collections.emptySet());
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
