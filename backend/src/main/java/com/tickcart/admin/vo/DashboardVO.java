package com.tickcart.admin.vo;

import com.tickcart.product.vo.CategoryOverview;
import com.tickcart.seckill.vo.SeckillOverviewVO;
import com.tickcart.trade.vo.DailyPoint;
import com.tickcart.trade.vo.OrderOverviewVO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 后台运营看板聚合响应。
 *
 * <p>一次 {@code GET /api/admin/dashboard} 拉齐订单 / 秒杀 / 库存 / 趋势，
 * 各域数据复用对应 service 的 overview，趋势由 {@code DashboardTrendService} 提供。</p>
 */
@Schema(description = "运营看板聚合响应")
public class DashboardVO {

    @Schema(description = "订单概览（复用 OrderService.adminOverview）")
    private OrderOverviewVO order;

    @Schema(description = "秒杀战报（复用 SeckillActivityService.overview）")
    private SeckillOverviewVO seckill;

    @Schema(description = "分类库存概览（复用 CategoryService.overview）")
    private List<CategoryOverview> category;

    @Schema(description = "近 N 天每日订单量")
    private List<DailyPoint> orderTrend;

    @Schema(description = "近 N 天每日成交额")
    private List<DailyPoint> amountTrend;

    public OrderOverviewVO getOrder() {
        return order;
    }

    public void setOrder(OrderOverviewVO order) {
        this.order = order;
    }

    public SeckillOverviewVO getSeckill() {
        return seckill;
    }

    public void setSeckill(SeckillOverviewVO seckill) {
        this.seckill = seckill;
    }

    public List<CategoryOverview> getCategory() {
        return category;
    }

    public void setCategory(List<CategoryOverview> category) {
        this.category = category;
    }

    public List<DailyPoint> getOrderTrend() {
        return orderTrend;
    }

    public void setOrderTrend(List<DailyPoint> orderTrend) {
        this.orderTrend = orderTrend;
    }

    public List<DailyPoint> getAmountTrend() {
        return amountTrend;
    }

    public void setAmountTrend(List<DailyPoint> amountTrend) {
        this.amountTrend = amountTrend;
    }
}
