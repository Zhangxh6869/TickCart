package com.tickcart.trade.enums;

import com.tickcart.common.exception.BizException;

import java.util.HashMap;
import java.util.Map;

/**
 * 退款原因码。
 *
 * <p>只存码不存文案：文案会改，码不会。前端提交码、后端落码、展示时用 {@link #getLabel()}
 * 还原，避免「历史记录里留着旧文案」。</p>
 */
public enum RefundReason {

    /** 不想要了：最高频，尤其是发货前反悔 */
    NO_LONGER_WANTED(1, "不想要了"),

    /** 信息填错：地址、手机号、规格选错 */
    WRONG_INFO(2, "地址或信息填错"),

    /** 商品有问题：少发、破损、与描述不符 */
    PRODUCT_ISSUE(3, "商品有问题"),

    /** 其他：必须填补充说明 */
    OTHER(4, "其他");

    private final int code;
    private final String label;

    private static final Map<Integer, RefundReason> BY_CODE = new HashMap<>();

    static {
        for (RefundReason reason : values()) {
            BY_CODE.put(reason.code, reason);
        }
    }

    RefundReason(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 按原因码取枚举；未知码抛业务异常。 */
    public static RefundReason of(int code) {
        RefundReason reason = BY_CODE.get(code);
        if (reason == null) {
            throw BizException.badRequest("未知的退款原因：" + code);
        }
        return reason;
    }
}
