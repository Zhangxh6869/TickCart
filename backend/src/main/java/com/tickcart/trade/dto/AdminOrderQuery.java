package com.tickcart.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 后台订单列表查询条件。
 *
 * <p>在 {@link OrderQuery}（状态 + 分页）基础上增加关键字，
 * 用于按订单号 / 收货人 / 收货人手机号模糊检索。</p>
 */
@Schema(description = "后台订单列表查询条件")
public class AdminOrderQuery extends OrderQuery {

    @Schema(description = "关键字：订单号 / 收货人 / 手机号，不传表示不过滤", example = "NO2026")
    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
