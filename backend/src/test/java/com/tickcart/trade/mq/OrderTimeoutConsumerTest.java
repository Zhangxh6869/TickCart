package com.tickcart.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.tickcart.common.mq.OrderMqConfig;
import com.tickcart.common.mq.OrderTimeoutMessage;
import com.tickcart.trade.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link OrderTimeoutConsumer} 单元测试。
 *
 * <p>覆盖手动 ack 的成功路径、失败重投（累加重试次数）以及重试耗尽后的丢弃。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单超时关单消费者")
class OrderTimeoutConsumerTest {

    private static final long DELIVERY_TAG = 7L;

    @Mock
    private OrderService orderService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private Channel channel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 手动装配：ObjectMapper 需要真实实例，不能交给 Mockito 注入。 */
    private OrderTimeoutConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderTimeoutConsumer(orderService, rabbitTemplate, objectMapper);
    }

    /** 构造一条与生产者投递格式一致的消息。 */
    private Message message(String orderNo, Integer retry) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        if (retry != null) {
            properties.setHeader(OrderMqConfig.HEADER_RETRY, retry);
        }
        return new Message(objectMapper.writeValueAsBytes(new OrderTimeoutMessage(orderNo)), properties);
    }

    @Test
    @DisplayName("关单成功：手动 ack 且不重投")
    void ackOnSuccess() throws Exception {
        consumer.onOrderTimeout(message("NO1", null), channel);

        verify(orderService).closeTimeout("NO1");
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(rabbitTemplate, never()).send(any(String.class), any(String.class), any(Message.class));
    }

    @Test
    @DisplayName("处理失败：重投到重试队列并累加重试次数，随后 ack")
    void republishOnFailure() throws Exception {
        doThrow(new IllegalStateException("DB 抖动")).when(orderService).closeTimeout("NO2");

        consumer.onOrderTimeout(message("NO2", null), channel);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(OrderMqConfig.EXCHANGE_DLX),
                eq(OrderMqConfig.RK_ORDER_TIMEOUT_RETRY), captor.capture());
        assertThat((Object) captor.getValue().getMessageProperties().getHeader(OrderMqConfig.HEADER_RETRY))
                .isEqualTo(1);
        assertThat(objectMapper.readValue(captor.getValue().getBody(), OrderTimeoutMessage.class).orderNo())
                .isEqualTo("NO2");
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("第 3 次失败仍在重试上限内，继续重投")
    void republishOnThirdFailure() throws Exception {
        doThrow(new IllegalStateException("DB 抖动")).when(orderService).closeTimeout("NO4");

        consumer.onOrderTimeout(message("NO4", 2), channel);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(OrderMqConfig.EXCHANGE_DLX),
                eq(OrderMqConfig.RK_ORDER_TIMEOUT_RETRY), captor.capture());
        assertThat((Object) captor.getValue().getMessageProperties().getHeader(OrderMqConfig.HEADER_RETRY))
                .isEqualTo(3);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("重试耗尽：丢弃消息且不重投")
    void discardWhenRetryExhausted() throws Exception {
        doThrow(new IllegalStateException("DB 抖动")).when(orderService).closeTimeout("NO3");

        consumer.onOrderTimeout(message("NO3", 3), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(rabbitTemplate, never()).send(any(String.class), any(String.class), any(Message.class));
    }
}
