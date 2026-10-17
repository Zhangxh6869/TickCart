package com.tickcart.auth.service;

/**
 * 短信验证码频控闸门。
 *
 * <p>抽成接口的理由与 {@link LoginGuard} 一致：{@code AuthService} 的单元测试不该依赖 Redis。</p>
 *
 * <p>调用约定：</p>
 * <ol>
 *   <li>发送前 {@link #tryAcquireSend}，返回 false 直接拒绝（此时还没生成码、没落库、没发短信）</li>
 *   <li>发送成功 {@link #recordSent} 累加窗口计数</li>
 *   <li>发送失败 {@link #releaseSend} 释放间隔占位，避免用户白等 30 秒</li>
 * </ol>
 */
public interface SmsCodeGuard {

    /**
     * 尝试占用一次发送机会。
     *
     * <p>「检查窗口/日上限」与「占用间隔位」在一次调用内完成，且间隔位用 SET NX 原子占位，
     * 两个并发请求只有一个能通过。</p>
     *
     * @return true 表示可以发送；false 表示被限流
     */
    boolean tryAcquireSend(String phone);

    /** 发送成功后累加窗口与日计数。 */
    void recordSent(String phone);

    /** 发送失败时释放间隔占位（窗口/日计数不受影响，因为压根没发出去）。 */
    void releaseSend(String phone);

    /**
     * 记录一次验证码校验失败。
     *
     * @param codeId 当前验证码记录的主键，失败次数跟着「这一条码」走
     * @return true 表示已达到上限，调用方应作废该码
     */
    boolean recordVerifyFailure(long codeId);

    /** 校验成功后清除该码的失败计数。 */
    void clearVerifyFailure(long codeId);

    /** 被限流时还要等多少秒，用于 429 文案与前端倒计时。 */
    long getRetryAfterSeconds(String phone);
}
