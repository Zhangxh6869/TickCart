package com.tickcart.cart.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车汇总：列表 + 勾选合计。
 *
 * <p>合计口径为「勾选且有效」的项，由服务端计算，避免前端因价格过期而算错。</p>
 */
@Schema(description = "购物车汇总")
public class CartSummary {

    @Schema(description = "购物车项列表（含失效项）")
    private List<CartItemVO> items;

    @Schema(description = "勾选且有效的总件数")
    private int checkedCount;

    @Schema(description = "勾选且有效的合计金额")
    private BigDecimal checkedAmount;

    @Schema(description = "购物车项总条数（含未勾选与失效项）")
    private int totalCount;

    public List<CartItemVO> getItems() {
        return items;
    }

    public void setItems(List<CartItemVO> items) {
        this.items = items;
    }

    public int getCheckedCount() {
        return checkedCount;
    }

    public void setCheckedCount(int checkedCount) {
        this.checkedCount = checkedCount;
    }

    public BigDecimal getCheckedAmount() {
        return checkedAmount;
    }

    public void setCheckedAmount(BigDecimal checkedAmount) {
        this.checkedAmount = checkedAmount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
