package com.tickcart.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickcart.common.mq.OrderMqConfig;
import com.tickcart.common.mq.OrderTimeoutMessage;
import com.tickcart.support.AbstractIntegrationTest;
import com.tickcart.support.Await;
import com.tickcart.support.InventoryInvariantChecker;
import com.tickcart.support.TestDataFactory;
import com.tickcart.trade.dto.OrderCreateRequest;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.service.OrderService;
import com.tickcart.trade.vo.OrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G3 异步闸门 · A1/A2：超时关单链路与重复消费幂等。
 *
 * <p>为什么直接投递到死信交换机、而不是等 TTL 到期：延迟队列 TTL 是 15 分钟，
 * 真实等待不可行；而改配置把 TTL 调短又会导致队列参数与已声明的队列不一致
 * （RabbitMQ 会抛 {@code PRECONDITION_FAILED}）。TTL 是 RabbitMQ 的内置能力，
 * 配对了就行，真正容易出问题的是「消费逻辑 / 幂等 / 死信路由 / ack」这一段，
 * 所以这里把消息直接投到 {@code trade.dlx}，测消费链路本身。</p>
 *
 * <p>验证的核心是 {@code OrderService.closeTimeout} 的幂等性：它用带状态条件的 UPDATE
 * 抢占（{@code WHERE status = PENDING_PAYMENT}），只有抢到状态流转才回补库存。
 * 若这条不成立，重复投递会把库存越补越多，表现为「库存凭空增加」——
 * 比超卖更隐蔽，因为系统不报错。</p>
 */
class OrderTimeoutIT extends AbstractIntegrationTest {

    private static final int INITIAL_STOCK = 100;
    private static final int BUY_QUANTITY = 2;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ObjectMapper objectMapper;

    private TestDataFactory data;
    private InventoryInvariantChecker invariants;

    @BeforeEach
    void setUp() {
        data = new TestDataFactory(jdbc);
        invariants = new InventoryInvariantChecker(jdbc);
        cleanBusinessTables();
    }

    @Test
    @DisplayName("A1 超时消息被消费后：订单关闭 + 库存回补")
    void timeoutMessageClosesOrderAndRestoresStock() throws Exception {
        TestDataFactory.BuyerFixture buyer = data.createBuyer(INITIAL_STOCK, BUY_QUANTITY);
        String orderNo = placeOrder(buyer);
        long skuId = buyer.product().skuId();

        assertThat(invariants.skuStock(skuId))
                .as("下单后库存应被扣减")
                .isEqualTo(INITIAL_STOCK - BUY_QUANTITY);
        assertThat(orderStatus(orderNo)).isEqualTo(OrderStatus.PENDING_PAYMENT.getCode());

        publishTimeoutDeadLetter(orderNo);

        Await.until("订单被超时关闭", Duration.ofSeconds(20),
                () -> orderStatus(orderNo) == OrderStatus.TIMEOUT_CLOSED.getCode());

        assertThat(invariants.skuStock(skuId))
                .as("关单后库存应回补到下单前")
                .isEqualTo(INITIAL_STOCK);

        invariants.assertAll();
    }

    @Test
    @DisplayName("A2 同一超时消息重复投递：只关一次单，库存只回补一次")
    void duplicateTimeoutMessageIsIdempotent() throws Exception {
        TestDataFactory.BuyerFixture buyer = data.createBuyer(INITIAL_STOCK, BUY_QUANTITY);
        String orderNo = placeOrder(buyer);
        long skuId = buyer.product().skuId();

        // 模拟 MQ 的 at-least-once 语义：同一条消息投两次
        publishTimeoutDeadLetter(orderNo);
        publishTimeoutDeadLetter(orderNo);

        Await.until("订单被超时关闭", Duration.ofSeconds(20),
                () -> orderStatus(orderNo) == OrderStatus.TIMEOUT_CLOSED.getCode());

        // 等两条消息都从队列里被取走，避免「第二条还没消费完就断言」造成假绿
        Await.until("死信队列被消费空", Duration.ofSeconds(20), () -> {
            QueueInformation info = rabbitAdmin.getQueueInfo(OrderMqConfig.QUEUE_ORDER_TIMEOUT_DLX);
            return info != null && info.getMessageCount() == 0;
        });
        // 队列计数归零时消息可能仍在消费中，留一点余量再断言
        Thread.sleep(500);

        assertThat(invariants.skuStock(skuId))
                .as("重复消息不得重复回补库存（应回到 %d，而不是 %d）",
                        INITIAL_STOCK, INITIAL_STOCK + BUY_QUANTITY)
                .isEqualTo(INITIAL_STOCK);

        assertThat(orderStatus(orderNo))
                .as("订单状态应保持已超时关闭")
                .isEqualTo(OrderStatus.TIMEOUT_CLOSED.getCode());

        invariants.assertAll();
    }

    /** 正常下单（走完整链路，不传幂等键）。 */
    private String placeOrder(TestDataFactory.BuyerFixture buyer) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(buyer.addressId());
        OrderVO vo = orderService.create(buyer.userId(), req, null);
        return vo.getOrderNo();
    }

    /** 把一条超时消息直接投到死信交换机，等价于「TTL 到期后转入死信队列」。 */
    private void publishTimeoutDeadLetter(String orderNo) throws Exception {
        String json = objectMapper.writeValueAsString(new OrderTimeoutMessage(orderNo));
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(
                OrderMqConfig.EXCHANGE_DLX,
                OrderMqConfig.RK_ORDER_TIMEOUT_DEAD,
                new Message(json.getBytes(StandardCharsets.UTF_8), props));
    }

    private int orderStatus(String orderNo) {
        Integer status = jdbc.queryForObject(
                "SELECT status FROM t_order WHERE order_no = ?", Integer.class, orderNo);
        return status == null ? -1 : status;
    }
}
