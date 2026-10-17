package com.tickcart.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录频控与人机验证配置，对应 {@code tickcart.login-guard.*}。
 *
 * <p>所有阈值都可配：不同环境对「误伤」的容忍度差别很大（开发环境要跑回归脚本，
 * 阈值放宽；生产环境要防爆破，阈值收紧），写死在代码里会导致两边总有一边难用。</p>
 */
@Component
@ConfigurationProperties(prefix = "tickcart.login-guard")
public class LoginGuardProperties {

    /** 总开关。关闭后所有频控与人机验证都不生效（仅供压测/排障使用）。 */
    private boolean enabled = true;

    /** 失败计数窗口（分钟）：账号与 IP 维度的计数都在这个窗口内累加。 */
    private int failWindowMinutes = 15;

    /** 账号在窗口内失败多少次后，要求先过图形码（R1）。 */
    private int verifyThreshold = 3;

    /** 账号在窗口内失败多少次后，直接锁定（R2）。 */
    private int lockThreshold = 5;

    /** 首轮锁定时长（分钟）。 */
    private int lockMinutes = 15;

    /** 第二轮锁定时长（分钟），用于阶梯惩罚。 */
    private int escalateLockMinutes = 60;

    /** 锁定上限（分钟），第三轮及以后都用它。 */
    private int maxLockMinutes = 1440;

    /** 阶梯轮次的记忆窗口（小时）：超过这个时间没再触发，惩罚轮次归零。 */
    private int escalateWindowHours = 24;

    /** 同 IP 在窗口内失败多少次后封禁（R5）。 */
    private int ipFailThreshold = 20;

    /** IP 封禁时长（分钟）。 */
    private int ipLockMinutes = 30;

    /** 同 IP 登录接口每秒允许的请求数（R6），用于挡住把 BCrypt 当 CPU 炸弹的打法。 */
    private int ipQpsLimit = 20;

    /** 图形码有效期（秒）。 */
    private int captchaExpireSeconds = 300;

    /** 图形码字符数。 */
    private int captchaLength = 4;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFailWindowMinutes() {
        return failWindowMinutes;
    }

    public void setFailWindowMinutes(int failWindowMinutes) {
        this.failWindowMinutes = failWindowMinutes;
    }

    public int getVerifyThreshold() {
        return verifyThreshold;
    }

    public void setVerifyThreshold(int verifyThreshold) {
        this.verifyThreshold = verifyThreshold;
    }

    public int getLockThreshold() {
        return lockThreshold;
    }

    public void setLockThreshold(int lockThreshold) {
        this.lockThreshold = lockThreshold;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        this.lockMinutes = lockMinutes;
    }

    public int getEscalateLockMinutes() {
        return escalateLockMinutes;
    }

    public void setEscalateLockMinutes(int escalateLockMinutes) {
        this.escalateLockMinutes = escalateLockMinutes;
    }

    public int getMaxLockMinutes() {
        return maxLockMinutes;
    }

    public void setMaxLockMinutes(int maxLockMinutes) {
        this.maxLockMinutes = maxLockMinutes;
    }

    public int getEscalateWindowHours() {
        return escalateWindowHours;
    }

    public void setEscalateWindowHours(int escalateWindowHours) {
        this.escalateWindowHours = escalateWindowHours;
    }

    public int getIpFailThreshold() {
        return ipFailThreshold;
    }

    public void setIpFailThreshold(int ipFailThreshold) {
        this.ipFailThreshold = ipFailThreshold;
    }

    public int getIpLockMinutes() {
        return ipLockMinutes;
    }

    public void setIpLockMinutes(int ipLockMinutes) {
        this.ipLockMinutes = ipLockMinutes;
    }

    public int getIpQpsLimit() {
        return ipQpsLimit;
    }

    public void setIpQpsLimit(int ipQpsLimit) {
        this.ipQpsLimit = ipQpsLimit;
    }

    public int getCaptchaExpireSeconds() {
        return captchaExpireSeconds;
    }

    public void setCaptchaExpireSeconds(int captchaExpireSeconds) {
        this.captchaExpireSeconds = captchaExpireSeconds;
    }

    public int getCaptchaLength() {
        return captchaLength;
    }

    public void setCaptchaLength(int captchaLength) {
        this.captchaLength = captchaLength;
    }
}
