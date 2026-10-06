package com.tickcart.common.mq;

/**
 * 订单超时消息体。
 *
 * <p>只携带订单号：消费侧据此回查订单最新状态，避免消息体里的状态快照过期。</p>
 *
 * @param orderNo 订单号
 */
public record OrderTimeoutMessage(String orderNo) {
}
