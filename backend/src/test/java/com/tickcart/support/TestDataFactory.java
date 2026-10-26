package com.tickcart.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 集成测试造数工厂。
 *
 * <p>为什么不用 Flyway 种子数据：种子里的库存、用户都是固定值，多个测试共享同一份数据
 * 会互相干扰（并发测试尤其致命）。这里每个测试自己造一份，测完清掉，互不影响。</p>
 *
 * <p>注意 {@code product.stock} 必须与 SKU 库存合计一致——它是不变量 I1 的内容，
 * 造数时故意造得不一致会让后续断言失去意义。</p>
 */
public class TestDataFactory {

    private final JdbcTemplate jdbc;
    private int seq = 0;

    public TestDataFactory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 造一个普通用户，返回 userId。 */
    public long createUser() {
        String username = "it_user_" + (++seq) + "_" + System.nanoTime();
        return insertAndReturnId(
                "INSERT INTO sys_user (username, password, nickname, gender, role, status) "
                        + "VALUES (?, ?, ?, 0, 0, 0)",
                username, "$2a$10$0123456789012345678901234567890123456789012345678901", username);
    }

    /** 造一个收货地址，返回 addressId。 */
    public long createAddress(long userId) {
        return insertAndReturnId(
                "INSERT INTO user_address (user_id, receiver, phone, province, city, district, detail, is_default) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
                userId, "集成测试", "13800000000", "广东省", "深圳市", "南山区", "科技园路 1 号");
    }

    /** 造一个上架商品 + 一个 SKU，库存由调用方指定。 */
    public ProductFixture createProduct(int skuStock) {
        long categoryId = insertAndReturnId(
                "INSERT INTO product_category (parent_id, name, level, sort, status) VALUES (0, ?, 1, 0, 1)",
                "IT分类" + (++seq));

        BigDecimal price = new BigDecimal("99.00");
        long productId = insertAndReturnId(
                "INSERT INTO product (category_id, name, price, stock, sales, status, is_seckill) "
                        + "VALUES (?, ?, ?, ?, 0, 1, 0)",
                categoryId, "IT商品" + (++seq), price, skuStock);

        long skuId = insertAndReturnId(
                "INSERT INTO product_sku (product_id, spec_name, spec_value, price, stock) "
                        + "VALUES (?, ?, ?, ?, ?)",
                productId, "颜色", "黑色", price, skuStock);

        return new ProductFixture(productId, skuId, skuStock);
    }

    /** 把商品放进购物车并勾选（下单只结算勾选项）。 */
    public void addToCart(long userId, ProductFixture product, int quantity) {
        jdbc.update(
                "INSERT INTO cart_item (user_id, product_id, sku_id, quantity, checked) VALUES (?, ?, ?, ?, 1)",
                userId, product.productId(), product.skuId(), quantity);
    }

    /** 一次性造好「一个能下单的用户」：用户 + 地址 + 商品 + 已勾选购物车项。 */
    public BuyerFixture createBuyer(int skuStock, int cartQuantity) {
        long userId = createUser();
        long addressId = createAddress(userId);
        ProductFixture product = createProduct(skuStock);
        addToCart(userId, product, cartQuantity);
        return new BuyerFixture(userId, addressId, product);
    }

    /** 按订单号取主键，用于给支付流水这类子表挂关联字段。 */
    public long orderIdOf(String orderNo) {
        Long id = jdbc.queryForObject(
                "SELECT id FROM t_order WHERE order_no = ?", Long.class, orderNo);
        return Objects.requireNonNull(id, "订单不存在：" + orderNo);
    }

    /** 插入一条支付流水，status 用 {@code PaymentRecord.STATUS_*} 常量。 */
    public long insertPaymentRecord(long orderId, String orderNo, String payNo, int status) {
        return insertAndReturnId(
                "INSERT INTO payment_record (order_id, order_no, pay_no, channel, amount, status) "
                        + "VALUES (?, ?, ?, 0, ?, ?)",
                orderId, orderNo, payNo, new BigDecimal("99.00"), status);
    }

    /** 直接插入一场「已上线」的秒杀活动，用于构造重叠校验的前置数据。 */
    public long insertOnlineSeckillActivity(long productId, long skuId,
                                            LocalDateTime start, LocalDateTime end) {
        return insertAndReturnId(
                "INSERT INTO seckill_activity (title, product_id, sku_id, seckill_price, seckill_stock, "
                        + "limit_per_user, start_time, end_time, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)",
                "IT秒杀" + (++seq), productId, skuId, new BigDecimal("9.90"), 10, 1,
                Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    private long insertAndReturnId(String sql, Object... args) {        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey(), "插入未返回主键").longValue();
    }

    /** 商品 + SKU 的标识与初始库存。 */
    public record ProductFixture(long productId, long skuId, int initialStock) {
    }

    /** 可直接用于下单的买家上下文。 */
    public record BuyerFixture(long userId, long addressId, ProductFixture product) {
    }
}
