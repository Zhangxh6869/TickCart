package com.tickcart.auth.service;

/**
 * 登录频控闸门。
 *
 * <p>抽成接口是为了让 {@code AuthService} 的单元测试不必依赖 Redis：
 * 生产实现是 {@link RedisLoginGuard}，测试可注入 Noop 或 Mockito 桩。</p>
 *
 * <p>调用顺序约定：{@link #assertQps} → {@link #assertNotLocked} →
 * 校验图形码 → 比对密码 → {@link #recordFailure} / {@link #recordSuccess}。</p>
 */
public interface LoginGuard {

    /** 同 IP 每秒请求数超限则抛 {@code RateLimitedException}。 */
    void assertQps(String ip);

    /** 账号（IP+账号组合）或 IP 处于锁定状态则抛 {@code RateLimitedException}。 */
    void assertNotLocked(String ip, String username);

    /** 该账号是否已失败到需要人机验证的程度。 */
    boolean isVerifyRequired(String ip, String username);

    /** 记录一次凭据错误。仅「密码错」计入，账号禁用、已锁定不计。 */
    void recordFailure(String ip, String username);

    /** 记录一次成功登录：清账号失败计数与惩罚轮次，不动 IP 计数。 */
    void recordSuccess(String ip, String username);

    /** 当前剩余锁定秒数，未锁定返回 0。 */
    long getRetryAfterSeconds(String ip, String username);
}
