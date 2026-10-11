package com.tickcart.common.mq;

/**
 * 支付渠道通知消息体（延迟投递）。
 *
 * <p>渠道侧确认后并不立即回调，而是先投到延迟队列、滞留 TTL 之后才真正触发回调 ——
 * 以此模拟真实渠道「用户付完款后 1~3 秒才通知商户」的异步特性。
 * 前端在这段窗口里只能轮询，拿不到结果。</p>
 *
 * @param payNo  支付流水号
 * @param result 渠道结果取值：{@code success} / {@code fail} / {@code cancel}
 */
public record PaymentNotifyMessage(String payNo, String result) {
}
