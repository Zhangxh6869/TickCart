package com.tickcart.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.mq.OrderMqConfig;
import com.tickcart.seckill.service.SeckillResultService;
import com.tickcart.seckill.service.SeckillStockService;
import com.tickcart.trade.service.OrderService;
import com.tickcart.trade.vo.OrderVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀建单消费者。
 *
 * <p>把「抢到名额」变成「真实订单」：抢购接口只做 Redis 扣减 + 投递消息，
 * 建单在这里异步完成 —— 这是秒杀削峰的关键，DB 写入速度不再决定抢购成功率。</p>
 *
 * <p><b>失败语义</b>：</p>
 * <ul>
 *   <li>{@link BizException}（无收货地址、商品不存在）→ 业务拒绝，重试也没用：
 *       回补库存 + 写失败结果 + ack</li>
 *   <li>其他异常（DB 抖动、网络）→ 重投重试队列，最多 3 次；耗尽后回补 + 失败结果</li>
 * </ul>
 *
 * <p><b>为什么失败回补只动 Redis、不动 {@code sold_count}</b>：{@code sold_count} 的累加
 * 发生在建单事务内，事务回滚时它一起回滚了；这里再减一次就会把统计减穿。</p>
 */
@ConditionalOnProperty(
        name = "tickcart.seckill.consumer-enabled",
        havingValue = "true",
        matchIfMissing = true)
@Component
public class SeckillOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    /** 最大重试次数 */
    private static final int MAX_RETRY = 3;

    private final OrderService orderService;
    private final SeckillStockService stockService;
    private final SeckillResultService resultService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public SeckillOrderConsumer(OrderService orderService,
                                SeckillStockService stockService,
                                SeckillResultService resultService,
                                RabbitTemplate rabbitTemplate,
                                ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.stockService = stockService;
        this.resultService = resultService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 消费建单消息。
     *
     * <p>容器工厂用 {@code seckillListenerContainerFactory}：prefetch 单独设 5，
     * 因为建单是「DB 写 + 事务」的重操作，一次抓太多只会把消息堆在内存里。</p>
     */
    @RabbitListener(queues = SeckillMqConfig.QUEUE_SECKILL_ORDER,
            containerFactory = "seckillListenerContainerFactory")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        SeckillOrderMessage payload;
        try {
            payload = objectMapper.readValue(message.getBody(), SeckillOrderMessage.class);
        } catch (Exception e) {
            // 消息体坏了，重试也不会变好
            log.error("秒杀建单消息解析失败，丢弃", e);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            OrderVO order = orderService.createSeckillOrder(
                    payload.getUserId(), payload.getActivityId(), payload.getProductId(),
                    payload.getSkuId(), payload.getAddressId(), payload.getQuantity(),
                    payload.getSeckillPrice(), payload.getRequestId());
            resultService.success(payload.getRequestId(), payload.getUserId(), order.getOrderNo());
            channel.basicAck(deliveryTag, false);
            log.info("秒杀建单成功：requestId={}, orderNo={}", payload.getRequestId(), order.getOrderNo());
        } catch (BizException e) {
            // 业务拒绝：地址缺失、商品被删等，重试无意义
            log.warn("秒杀建单被业务拒绝：requestId={}, reason={}", payload.getRequestId(), e.getMessage());
            giveUp(payload, e.getMessage());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            int retry = retryCountOf(message) + 1;
            if (retry <= MAX_RETRY) {
                log.warn("秒杀建单失败，第 {}/{} 次重试：requestId={}, cause={}",
                        retry, MAX_RETRY, payload.getRequestId(), e.getMessage());
                republishForRetry(message, retry);
                channel.basicAck(deliveryTag, false);
            } else {
                log.error("秒杀建单重试 {} 次仍失败：requestId={}", MAX_RETRY, payload.getRequestId(), e);
                giveUp(payload, "下单失败，库存已退回");
                channel.basicAck(deliveryTag, false);
            }
        }
    }

    /**
     * 最终失败：回补 Redis 库存 + 写失败结果，让用户能重新抢。
     *
     * <p>只回补 Redis，不减 {@code sold_count} —— 见类注释。</p>
     */
    private void giveUp(SeckillOrderMessage payload, String reason) {
        stockService.restore(payload.getActivityId(), payload.getUserId(), payload.getQuantity());
        resultService.failed(payload.getRequestId(), payload.getUserId(), reason);
    }

    private int retryCountOf(Message message) {
        Object value = message.getMessageProperties().getHeader(OrderMqConfig.HEADER_RETRY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /**
     * 重投到重试队列（携带累加后的重试次数）。
     *
     * <p>只搬运原始 body：不复制 {@code x-death} 等死信头（RabbitMQ 会校验其格式）。</p>
     */
    private void republishForRetry(Message message, int retry) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader(OrderMqConfig.HEADER_RETRY, retry);
        rabbitTemplate.send(OrderMqConfig.EXCHANGE_TRADE, SeckillMqConfig.RK_SECKILL_ORDER_RETRY,
                new Message(message.getBody(), properties));
    }
}
