package com.tickcart.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存不变量检查器。
 *
 * <p>不写零散断言，而是把「任何时刻都必须成立」的等式集中在这里，每个测试场景结束后统一校验一次。
 * 这样新增测试时不用重复写断言，也不会漏掉某个维度。</p>
 *
 * <p>已有不变量：</p>
 * <ul>
 *   <li><b>I1</b> 对有规格商品：{@code product.stock == SUM(product_sku.stock WHERE deleted = 0)}。
 *       {@code product_sku.stock} 是真账，{@code product.stock} 是派生汇总，
 *       两者漂移过（见 commit e0c9314），这条就是防止复发。</li>
 *   <li><b>I2</b> 任何 SKU 库存不得为负。</li>
 *   <li><b>I3</b> 某个 SKU 的成交数量（未取消订单）不得超过初始备货量。</li>
 * </ul>
 */
public class InventoryInvariantChecker {

    /** 订单状态：4 已取消、5 已超时关闭——这两种不占用库存（已回补）。 */
    private static final String CANCELLED_STATUSES = "(4, 5)";

    private final JdbcTemplate jdbc;

    public InventoryInvariantChecker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** I1：商品汇总库存必须等于其未删除 SKU 的库存合计。 */
    public void assertProductStockMatchesSkuSum() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id                        AS product_id,
                       p.stock                     AS product_stock,
                       COALESCE(SUM(s.stock), 0)   AS sku_sum
                FROM product p
                JOIN product_sku s ON s.product_id = p.id AND s.deleted = 0
                GROUP BY p.id, p.stock
                """);

        for (Map<String, Object> row : rows) {
            long productId = ((Number) row.get("product_id")).longValue();
            int productStock = ((Number) row.get("product_stock")).intValue();
            int skuSum = ((Number) row.get("sku_sum")).intValue();

            assertThat(productStock)
                    .as("I1 违反：商品 %d 的 product.stock=%d 与 SKU 库存合计=%d 不一致",
                            productId, productStock, skuSum)
                    .isEqualTo(skuSum);
        }
    }

    /** I2：库存不得为负。 */
    public void assertNoNegativeStock() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, stock FROM product_sku WHERE deleted = 0 AND stock < 0");

        assertThat(rows)
                .as("I2 违反：存在负库存的 SKU，说明条件 UPDATE 的守卫失效")
                .isEmpty();
    }

    /**
     * I3：该 SKU 的成交数量不得超过初始备货量——这就是「超卖」的定义。
     *
     * @param skuId        被测 SKU
     * @param initialStock 造数时给的初始库存
     */
    public void assertSoldNotExceedInitialStock(long skuId, int initialStock) {
        Integer sold = jdbc.queryForObject("""
                SELECT COALESCE(SUM(oi.quantity), 0)
                FROM order_item oi
                JOIN t_order o ON o.id = oi.order_id
                WHERE oi.sku_id = ?
                  AND o.status NOT IN (4, 5)
                """, Integer.class, skuId);

        assertThat(sold)
                .as("I3 违反：SKU %d 成交 %s 件，超过初始备货 %d 件（超卖）", skuId, sold, initialStock)
                .isNotNull()
                .isLessThanOrEqualTo(initialStock);
    }

    /** 读取当前 SKU 库存，便于断言具体数值。 */
    public int skuStock(long skuId) {
        Integer stock = jdbc.queryForObject(
                "SELECT stock FROM product_sku WHERE id = ?", Integer.class, skuId);
        assertThat(stock).as("SKU %d 不存在", skuId).isNotNull();
        return stock;
    }

    /** 读取当前商品汇总库存。 */
    public int productStock(long productId) {
        Integer stock = jdbc.queryForObject(
                "SELECT stock FROM product WHERE id = ?", Integer.class, productId);
        assertThat(stock).as("商品 %d 不存在", productId).isNotNull();
        return stock;
    }

    /** 统计订单数（可按用户过滤，null 表示全部）。 */
    public int orderCount(Long userId) {
        Integer count = userId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM t_order", Integer.class)
                : jdbc.queryForObject("SELECT COUNT(*) FROM t_order WHERE user_id = ?",
                        Integer.class, userId);
        return count == null ? 0 : count;
    }

    /** 全量校验：所有不变量跑一遍。 */
    public void assertAll() {
        assertProductStockMatchesSkuSum();
        assertNoNegativeStock();
    }
}
