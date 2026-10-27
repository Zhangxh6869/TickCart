package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.vo.DailyPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营看板「按日趋势」查询。
 *
 * <p>两条趋势：近 N 天订单量（按 {@code created_at}）与近 N 天成交额（按 {@code pay_time}，
 * 成交口径 = 已付款订单）。查询走 {@code t_order} 的 {@code idx_created_at} 索引，
 * 返回的是「只含当天有数据」的行，由本类在内存里补出连续的 0 天，方便前端直接画图。</p>
 */
@Service
public class DashboardTrendService {

    private final OrderMapper orderMapper;

    public DashboardTrendService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * 近 {@code days} 天每日订单量（含全部状态）。
     */
    public List<DailyPoint> dailyOrderTrend(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<Map<String, Object>> rows = orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("DATE(created_at) AS day", "COUNT(*) AS cnt")
                .ge("created_at", start.atStartOfDay())
                .groupBy("DATE(created_at)")
                .orderByAsc("day"));

        Map<String, DailyPoint> byDay = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            String day = start.plusDays(i).toString();
            byDay.put(day, new DailyPoint(day, 0L, BigDecimal.ZERO));
        }
        for (Map<String, Object> row : rows) {
            String day = dateValue(row, "day");
            DailyPoint p = byDay.get(day);
            if (p != null) {
                p.setCount(numberValue(row, "cnt"));
            }
        }
        return new ArrayList<>(byDay.values());
    }

    /**
     * 近 {@code days} 天每日成交额（成交 = 已付款订单，即 {@code pay_time} 非空）。
     */
    public List<DailyPoint> dailyAmountTrend(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<Map<String, Object>> rows = orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("DATE(pay_time) AS day", "COUNT(*) AS cnt", "SUM(pay_amount) AS amt")
                .ge("pay_time", start.atStartOfDay())
                .groupBy("DATE(pay_time)")
                .orderByAsc("day"));

        Map<String, DailyPoint> byDay = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            String day = start.plusDays(i).toString();
            byDay.put(day, new DailyPoint(day, 0L, BigDecimal.ZERO));
        }
        for (Map<String, Object> row : rows) {
            String day = dateValue(row, "day");
            DailyPoint p = byDay.get(day);
            if (p != null) {
                p.setCount(numberValue(row, "cnt"));
                p.setAmount(decimalValue(row, "amt"));
            }
        }
        return new ArrayList<>(byDay.values());
    }

    /** 从 {@code selectMaps} 的一行里取日期列（DATE(...) 结果，驱动可能给 java.sql.Date 或 LocalDate）。 */
    private static String dateValue(Map<String, Object> row, String column) {
        Object value = find(row, column);
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().toString();
        }
        return value == null ? "" : value.toString();
    }

    /** 取行内某列（列名大小写无关，兼容不同驱动的 key 大小写差异）。 */
    private static long numberValue(Map<String, Object> row, String column) {
        Object value = find(row, column);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** 取金额列（SUM 结果为 BigDecimal，空值为 null）。 */
    private static BigDecimal decimalValue(Map<String, Object> row, String column) {
        Object value = find(row, column);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value instanceof Number number
                ? BigDecimal.valueOf(number.doubleValue())
                : BigDecimal.ZERO;
    }

    private static Object find(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(column)) {
                    return entry.getValue();
                }
            }
        }
        return value;
    }
}
