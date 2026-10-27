package com.tickcart.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 运营看板「按日趋势」的一个数据点。
 *
 * <p>订单量趋势只关心 {@code count}；成交额趋势额外带 {@code amount}。</p>
 */
@Schema(description = "按日趋势数据点")
public class DailyPoint {

    @Schema(description = "日期 yyyy-MM-dd")
    private String day;

    @Schema(description = "当日订单量 / 当日成交单数")
    private long count;

    @Schema(description = "当日成交额（仅成交额趋势使用）")
    private BigDecimal amount = BigDecimal.ZERO;

    public DailyPoint() {
    }

    public DailyPoint(String day, long count, BigDecimal amount) {
        this.day = day;
        this.count = count;
        this.amount = amount;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
