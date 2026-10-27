package com.tickcart.admin.vo;

import com.tickcart.product.vo.LowStockReport;
import com.tickcart.seckill.vo.SeckillActivityVO;
import com.tickcart.seckill.vo.SeckillOverviewVO;
import com.tickcart.trade.vo.DailyPoint;
import com.tickcart.trade.vo.OrderOverviewVO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 后台运营看板聚合响应。
 *
 * <p>一次 {@code GET /api/admin/dashboard} 拉齐订单 / 秒杀 / 库存 / 趋势，
 * 各域数据复用对应 service 的既有方法，趋势由 {@code DashboardTrendService} 提供。</p>
 */
@Schema(description = "运营看板聚合响应")
public class DashboardVO {

    @Schema(description = "订单概览（复用 OrderService.adminOverview）")
    private OrderOverviewVO order;

    @Schema(description = "秒杀战报汇总（复用 SeckillActivityService.overview）")
    private SeckillOverviewVO seckill;

    @Schema(description = "战报明细：按「进行中→即将开始→已结束→已下线」取前几场")
    private List<SeckillActivityVO> seckillActivities;


    @Schema(description = "库存告急明细（复用 ProductService.lowStock，SKU 级，带库存与预警线）")
    private LowStockReport lowStock;

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

    public List<SeckillActivityVO> getSeckillActivities() {
        return seckillActivities;
    }

    public void setSeckillActivities(List<SeckillActivityVO> seckillActivities) {
        this.seckillActivities = seckillActivities;
    }

    public LowStockReport getLowStock() {
        return lowStock;
    }

    public void setLowStock(LowStockReport lowStock) {
        this.lowStock = lowStock;
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
