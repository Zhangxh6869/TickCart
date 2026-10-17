package com.tickcart.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信验证码频控配置，对应 {@code tickcart.sms.guard.*}。
 *
 * <p>分两组：发送侧（同号间隔 / 窗口次数 / 日累计）防轰炸，校验侧（失败次数）防爆破。
 * 单独成套是因为只做一侧都能被绕过 —— 只限失败次数，攻击者可以「试 5 次 → 重新获取」；
 * 只限发送，单个码仍可无限试。</p>
 */
@Component
@ConfigurationProperties(prefix = "tickcart.sms.guard")
public class SmsGuardProperties {

    /** 总开关，关闭后所有短信频控不生效（仅供压测/排障）。 */
    private boolean enabled = true;

    /** 同号两次发送的最小间隔（秒）。S1 */
    private int sendIntervalSeconds = 30;

    /** 窗口长度（分钟）。S2 */
    private int sendWindowMinutes = 10;

    /** 窗口内同号最多发送次数。S2 */
    private int sendWindowLimit = 3;

    /** 同号每日最多发送次数。S3 */
    private int dailyLimit = 10;

    /** 同号对同一个码最多允许错几次，达到即作废。S5 */
    private int verifyFailLimit = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSendIntervalSeconds() {
        return sendIntervalSeconds;
    }

    public void setSendIntervalSeconds(int sendIntervalSeconds) {
        this.sendIntervalSeconds = sendIntervalSeconds;
    }

    public int getSendWindowMinutes() {
        return sendWindowMinutes;
    }

    public void setSendWindowMinutes(int sendWindowMinutes) {
        this.sendWindowMinutes = sendWindowMinutes;
    }

    public int getSendWindowLimit() {
        return sendWindowLimit;
    }

    public void setSendWindowLimit(int sendWindowLimit) {
        this.sendWindowLimit = sendWindowLimit;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public int getVerifyFailLimit() {
        return verifyFailLimit;
    }

    public void setVerifyFailLimit(int verifyFailLimit) {
        this.verifyFailLimit = verifyFailLimit;
    }
}
