package com.tickcart.it;

import com.tickcart.support.AbstractIntegrationTest;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.enums.RefundStatus;
import com.tickcart.trade.task.RefundReturnTimeoutTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 退货超时自动关闭 · 集成测试。
 *
 * <p><b>为什么单测不够</b>：这条链路的判断全在 SQL 里 —— 嵌套的
 * {@code (audit_time < ? OR (audit_time IS NULL AND updated_at < ?))}、
 * {@code LIMIT}、以及 {@code active_key} 唯一索引的释放。Mockito 里 wrapper 只是一个对象，
 * 参数对不对、括号加没加、MySQL 会不会因为 OR 把索引走坏，都验证不了。
 * 这里用真实 MySQL 跑一遍，断言落到库里的结果。</p>
 *
 * <p><b>为什么不走完整下单链路造数</b>：本用例要验的是「扫描 + 关闭」这一段，
 * 前置条件只需要「订单在退款中 + 退款单在待寄回」。走一遍下单-支付-申请-同意
 * 会把测试拖长十倍，且失败时定位不到是本用例的问题还是链路的问题。所以直接插库。</p>
 */
@DisplayName("退货超时自动关闭 · 集成")
class RefundReturnTimeoutIT extends AbstractIntegrationTest {

    private static final String ORDER_PREFIX = "NOIT";

    @Autowired
    private RefundReturnTimeoutTask task;

    @BeforeEach
    void setUp() {
        cleanBusinessTables();
        jdbc.execute("TRUNCATE TABLE refund_record");
    }

    @Test
    @DisplayName("超时未寄回：退款单置已拒绝、释放 active_key、订单回退到已完成")
    void closesExpiredReturnRefund() {
        long userId = insertUser();
        long orderId = insertOrder(userId, ORDER_PREFIX + "001", OrderStatus.REFUNDING.getCode());
        // 10 天前同意的退货，超过默认 168 小时
        insertRefund(userId, orderId, "RFIT001", OrderStatus.REFUNDING.getCode(),
                OrderStatus.COMPLETED.getCode(), RefundStatus.WAIT_RETURN.getCode(),
                LocalDateTime.now().minusDays(10));

        task.closeExpired();

        assertThat(refundStatus("RFIT001"))
                .as("超时未寄回应被自动关闭为已拒绝")
                .isEqualTo(RefundStatus.REJECTED.getCode());
        assertThat(activeKey("RFIT001"))
                .as("★ 必须释放 active_key，否则该订单永远不能再申请售后")
                .isNull();
        assertThat(orderStatus(ORDER_PREFIX + "001"))
                .as("订单应回到申请前的状态（已完成），而不是留在退款中")
                .isEqualTo(OrderStatus.COMPLETED.getCode());
    }

    @Test
    @DisplayName("未超时的退货单不动（不能把还在期限内的用户判死）")
    void keepsRefundWithinDeadline() {
        long userId = insertUser();
        long orderId = insertOrder(userId, ORDER_PREFIX + "002", OrderStatus.REFUNDING.getCode());
        // 1 天前同意，远未到 168 小时
        insertRefund(userId, orderId, "RFIT002", OrderStatus.REFUNDING.getCode(),
                OrderStatus.COMPLETED.getCode(), RefundStatus.WAIT_RETURN.getCode(),
                LocalDateTime.now().minusDays(1));

        task.closeExpired();

        assertThat(refundStatus("RFIT002")).isEqualTo(RefundStatus.WAIT_RETURN.getCode());
        assertThat(activeKey("RFIT002")).isEqualTo(ORDER_PREFIX + "002");
        assertThat(orderStatus(ORDER_PREFIX + "002")).isEqualTo(OrderStatus.REFUNDING.getCode());
    }

    @Test
    @DisplayName("「待收货」即使超时也不动：货在路上，没有物流轨迹判不出来")
    void doesNotTouchWaitReceive() {
        long userId = insertUser();
        long orderId = insertOrder(userId, ORDER_PREFIX + "003", OrderStatus.REFUNDING.getCode());
        insertRefund(userId, orderId, "RFIT003", OrderStatus.REFUNDING.getCode(),
                OrderStatus.COMPLETED.getCode(), RefundStatus.WAIT_RECEIVE.getCode(),
                LocalDateTime.now().minusDays(30));

        task.closeExpired();

        assertThat(refundStatus("RFIT003"))
                .as("待收货不归这个任务管，硬关会把真在路上的货判死")
                .isEqualTo(RefundStatus.WAIT_RECEIVE.getCode());
        assertThat(orderStatus(ORDER_PREFIX + "003")).isEqualTo(OrderStatus.REFUNDING.getCode());
    }

    @Test
    @DisplayName("audit_time 为空的存量数据用 updated_at 兜底，不会被漏掉")
    void fallsBackToUpdatedAtWhenAuditTimeIsNull() {
        long userId = insertUser();
        long orderId = insertOrder(userId, ORDER_PREFIX + "004", OrderStatus.REFUNDING.getCode());
        long refundId = insertRefund(userId, orderId, "RFIT004",
                OrderStatus.REFUNDING.getCode(), OrderStatus.COMPLETED.getCode(),
                RefundStatus.WAIT_RETURN.getCode(), null);
        // 模拟「同意时没写 audit_time」的脏数据：updated_at 才是真正的最后变更时间
        jdbc.update("UPDATE refund_record SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(10), refundId);

        task.closeExpired();

        assertThat(refundStatus("RFIT004"))
                .as("audit_time 为空时若只按 audit_time 过滤，这类行会永远卡住")
                .isEqualTo(RefundStatus.REJECTED.getCode());
    }

    @Test
    @DisplayName("连跑两轮：第二轮无事可做，订单不会被重复回退")
    void secondRunIsNoop() {
        long userId = insertUser();
        long orderId = insertOrder(userId, ORDER_PREFIX + "005", OrderStatus.REFUNDING.getCode());
        insertRefund(userId, orderId, "RFIT005", OrderStatus.REFUNDING.getCode(),
                OrderStatus.COMPLETED.getCode(), RefundStatus.WAIT_RETURN.getCode(),
                LocalDateTime.now().minusDays(10));

        task.closeExpired();
        assertThatCode(task::closeExpired).doesNotThrowAnyException();

        assertThat(refundStatus("RFIT005")).isEqualTo(RefundStatus.REJECTED.getCode());
        assertThat(orderStatus(ORDER_PREFIX + "005")).isEqualTo(OrderStatus.COMPLETED.getCode());
    }

    @Test
    @DisplayName("关闭后同一订单可以再次申请售后（active_key 已释放）")
    void canReapplyAfterAutoClose() {
        long userId = insertUser();
        long orderId = insertOrder(userId, ORDER_PREFIX + "006", OrderStatus.REFUNDING.getCode());
        insertRefund(userId, orderId, "RFIT006", OrderStatus.REFUNDING.getCode(),
                OrderStatus.COMPLETED.getCode(), RefundStatus.WAIT_RETURN.getCode(),
                LocalDateTime.now().minusDays(10));

        task.closeExpired();

        // 再插一条进行中的退款单：uk_active_order 若没释放，这里会抛 DuplicateKey
        assertThatCode(() -> insertRefund(userId, orderId, "RFIT006B",
                OrderStatus.COMPLETED.getCode(), OrderStatus.COMPLETED.getCode(),
                RefundStatus.PENDING_AUDIT.getCode(), null))
                .doesNotThrowAnyException();
    }

    // ---------------- 造数与查询 ----------------

    private long insertUser() {
        // phone 上有唯一索引，用纳秒后缀避免同一轮里撞键
        String suffix = String.valueOf(System.nanoTime());
        jdbc.update("INSERT INTO sys_user (username, password, phone) VALUES (?, ?, ?)",
                "it_user_" + suffix, "x", "1" + suffix.substring(Math.max(0, suffix.length() - 10)));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertOrder(long userId, String orderNo, int status) {
        jdbc.update("INSERT INTO t_order (order_no, user_id, status, receiver, receiver_phone, "
                        + "receiver_addr) VALUES (?, ?, ?, ?, ?, ?)",
                orderNo, userId, status, "测试", "13900000000", "浙江省杭州市余杭区 1 号");
        return jdbc.queryForObject(
                "SELECT id FROM t_order WHERE order_no = ?", Long.class, orderNo);
    }

    private long insertRefund(long userId, long orderId, String refundNo, int orderStatus,
                              int originStatus, int refundStatus, LocalDateTime auditTime) {
        String orderNo = jdbc.queryForObject(
                "SELECT order_no FROM t_order WHERE id = ?", String.class, orderId);
        jdbc.update("INSERT INTO refund_record (refund_no, order_id, order_no, user_id, type, "
                        + "amount, reason_code, status, origin_status, active_key, audit_time) "
                        + "VALUES (?, ?, ?, ?, 3, 188.00, 1, ?, ?, ?, ?)",
                refundNo, orderId, orderNo, userId, refundStatus, originStatus,
                RefundStatus.of(refundStatus).isFinal() ? null : orderNo, auditTime);
        return jdbc.queryForObject(
                "SELECT id FROM refund_record WHERE refund_no = ?", Long.class, refundNo);
    }

    private int refundStatus(String refundNo) {
        return jdbc.queryForObject(
                "SELECT status FROM refund_record WHERE refund_no = ?", Integer.class, refundNo);
    }

    private String activeKey(String refundNo) {
        return jdbc.queryForObject(
                "SELECT active_key FROM refund_record WHERE refund_no = ?", String.class, refundNo);
    }

    private int orderStatus(String orderNo) {
        return jdbc.queryForObject(
                "SELECT status FROM t_order WHERE order_no = ?", Integer.class, orderNo);
    }
}
