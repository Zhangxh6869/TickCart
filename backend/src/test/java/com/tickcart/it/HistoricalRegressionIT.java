package com.tickcart.it;

import com.tickcart.common.exception.BizException;
import com.tickcart.seckill.dto.SeckillActivitySaveRequest;
import com.tickcart.seckill.service.SeckillActivityService;
import com.tickcart.support.AbstractIntegrationTest;
import com.tickcart.support.TestDataFactory;
import com.tickcart.trade.dto.OrderCreateRequest;
import com.tickcart.trade.entity.PaymentRecord;
import com.tickcart.trade.service.OrderService;
import com.tickcart.trade.vo.AdminOrderDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G6 历史 bug 回归。
 *
 * <p>修过但没写回归用例的 bug，等于没修——下一次重构就会回来。这里把已修复的缺陷
 * 逐条固化成用例，每条都刻意构造成「旧实现会失败、新实现才通过」的场景。</p>
 */
class HistoricalRegressionIT extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private SeckillActivityService seckillActivityService;

    private TestDataFactory data;

    @BeforeEach
    void setUp() {
        data = new TestDataFactory(jdbc);
        cleanBusinessTables();
    }

    @Test
    @DisplayName("G6-1 后台详情优先展示支付成功的流水，而不是 id 更大的失败流水")
    void adminDetailPrefersSuccessfulPayment() {
        TestDataFactory.BuyerFixture buyer = data.createBuyer(10, 1);
        String orderNo = placeOrder(buyer);
        long orderId = data.orderIdOf(orderNo);

        // 关键：成功流水先落库（id 小），失败流水后落库（id 大）。
        // 旧实现「只取 id 最大」，会选中失败那条 —— 于是已取消的订单也挂着一个流水号，
        // 后台读起来像「已付款后又取消了」，是纯误导。
        data.insertPaymentRecord(orderId, orderNo, "PAY-SUCCESS-" + orderNo, PaymentRecord.STATUS_SUCCESS);
        data.insertPaymentRecord(orderId, orderNo, "PAY-FAILED-" + orderNo, PaymentRecord.STATUS_FAILED);

        AdminOrderDetailVO detail = orderService.adminDetail(orderNo);

        assertThat(detail.getPayStatus())
                .as("应优先展示支付成功的流水（status=%d），而不是 id 更大的失败流水（status=%d）",
                        PaymentRecord.STATUS_SUCCESS, PaymentRecord.STATUS_FAILED)
                .isEqualTo(PaymentRecord.STATUS_SUCCESS);

        assertThat(detail.getPayNo())
                .as("流水号必须对应成功那条")
                .isNotNull()
                .startsWith("PAY-SUCCESS-");
    }

    @Test
    @DisplayName("G6-1 只有失败流水时：退回最新一条，用于展示最后尝试的渠道")
    void adminDetailFallsBackToLatestWhenNoSuccess() {
        TestDataFactory.BuyerFixture buyer = data.createBuyer(10, 1);
        String orderNo = placeOrder(buyer);
        long orderId = data.orderIdOf(orderNo);

        data.insertPaymentRecord(orderId, orderNo, "PAY-FAILED-OLD-" + orderNo, PaymentRecord.STATUS_FAILED);
        data.insertPaymentRecord(orderId, orderNo, "PAY-FAILED-NEW-" + orderNo, PaymentRecord.STATUS_FAILED);

        AdminOrderDetailVO detail = orderService.adminDetail(orderNo);

        assertThat(detail.getPayStatus())
                .as("没有成功记录时应退回最新一条失败流水")
                .isEqualTo(PaymentRecord.STATUS_FAILED);
        assertThat(detail.getPayNo())
                .as("应取 id 更大的那条")
                .startsWith("PAY-FAILED-NEW-");
    }

    private String placeOrder(TestDataFactory.BuyerFixture buyer) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(buyer.addressId());
        return orderService.create(buyer.userId(), req, null).getOrderNo();
    }

    @Test
    @DisplayName("G6-2 同 SKU 时间重叠的活动被拒绝；首尾相接不算重叠")
    void overlappingSeckillActivityIsRejected() {
        TestDataFactory.ProductFixture product = data.createProduct(100);
        LocalDateTime base = LocalDateTime.now().plusDays(1).withNano(0);

        // 已上线活动：[base+1h, base+3h)
        data.insertOnlineSeckillActivity(product.productId(), product.skuId(),
                base.plusHours(1), base.plusHours(3));

        // 与它重叠：[base+2h, base+4h) → 应被拒绝
        assertThatThrownBy(() -> seckillActivityService.create(
                saveRequest(product, base.plusHours(2), base.plusHours(4))))
                .as("同 SKU 时间重叠应被拒绝")
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已有一场上线中的活动");

        // 首尾相接：[base+3h, base+5h)，start == 已有活动的 end → 不算重叠
        Long created = seckillActivityService.create(
                saveRequest(product, base.plusHours(3), base.plusHours(5)));
        assertThat(created)
                .as("首尾相接（新活动 start == 已有活动 end）不应算重叠")
                .isNotNull();
    }

    private SeckillActivitySaveRequest saveRequest(TestDataFactory.ProductFixture product,
                                                   LocalDateTime start, LocalDateTime end) {
        SeckillActivitySaveRequest req = new SeckillActivitySaveRequest();
        req.setTitle("IT 重叠校验");
        req.setProductId(product.productId());
        req.setSkuId(product.skuId());
        req.setSeckillPrice(new BigDecimal("9.90"));
        req.setSeckillStock(10);
        req.setLimitPerUser(1);
        req.setStartTime(start);
        req.setEndTime(end);
        return req;
    }
}
