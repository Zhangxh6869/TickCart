package com.tickcart.common.exception;

import com.tickcart.common.api.ResultCode;

/**
 * 请求被频控拦截（登录锁定、IP 封禁、QPS 超限）。
 *
 * <p>携带剩余等待秒数，前端据此做倒计时并禁用按钮；只给文案的话前端要么解析中文字符串，
 * 要么只能让用户盲目重试。</p>
 */
public class RateLimitedException extends BizException {

    /** 还需等待的秒数（至少 1，避免前端倒计时出现 0 秒仍被拦）。 */
    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ResultCode.TOO_MANY_REQUESTS, buildMessage(retryAfterSeconds));
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** 按量级给出人话：秒级说秒，分钟级说分钟，小时级说小时。 */
    private static String buildMessage(long seconds) {
        long s = Math.max(1, seconds);
        if (s < 60) {
            return "操作过于频繁，请 " + s + " 秒后重试";
        }
        if (s < 3600) {
            return "尝试次数过多，请 " + ((s + 59) / 60) + " 分钟后重试";
        }
        return "尝试次数过多，请 " + ((s + 3599) / 3600) + " 小时后重试";
    }
}
