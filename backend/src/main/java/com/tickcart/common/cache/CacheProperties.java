package com.tickcart.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 缓存配置项，对应 {@code tickcart.cache.*}。
 *
 * <p>所有 TTL 都在这里配置，不硬编码（AGENTS.md 4.3）。改完重启即可生效，
 * 不需要改代码 —— 这是「缓存多久」这类运营参数应有的形态。</p>
 */
@Component
@ConfigurationProperties(prefix = "tickcart.cache")
public class CacheProperties {

    /** 总开关：关掉后所有读写直接回源，用于排查「是不是缓存导致的」。 */
    private boolean enabled = true;

    /** 商品详情 TTL（秒）。详情含库存，太长会显示过期库存。 */
    private long productDetailTtlSeconds = 60;

    /** 分类列表 TTL（秒）。分类极少变动，可以长一些。 */
    private long categoryListTtlSeconds = 1800;

    /** 热搜词 TTL（秒）。依赖搜索日志词频，太短等于没缓存、太长失去时效。 */
    private long hotWordsTtlSeconds = 300;

    /** 空值标记 TTL（秒），用于防缓存穿透。 */
    private long nullTtlSeconds = 60;

    /** TTL 随机抖动百分比，避免同一时刻批量过期造成雪崩。0 表示不抖动。 */
    private int ttlJitterPercent = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getProductDetailTtlSeconds() {
        return productDetailTtlSeconds;
    }

    public void setProductDetailTtlSeconds(long productDetailTtlSeconds) {
        this.productDetailTtlSeconds = productDetailTtlSeconds;
    }

    public long getCategoryListTtlSeconds() {
        return categoryListTtlSeconds;
    }

    public void setCategoryListTtlSeconds(long categoryListTtlSeconds) {
        this.categoryListTtlSeconds = categoryListTtlSeconds;
    }

    public long getHotWordsTtlSeconds() {
        return hotWordsTtlSeconds;
    }

    public void setHotWordsTtlSeconds(long hotWordsTtlSeconds) {
        this.hotWordsTtlSeconds = hotWordsTtlSeconds;
    }

    public long getNullTtlSeconds() {
        return nullTtlSeconds;
    }

    public void setNullTtlSeconds(long nullTtlSeconds) {
        this.nullTtlSeconds = nullTtlSeconds;
    }

    public int getTtlJitterPercent() {
        return ttlJitterPercent;
    }

    public void setTtlJitterPercent(int ttlJitterPercent) {
        this.ttlJitterPercent = ttlJitterPercent;
    }
}
