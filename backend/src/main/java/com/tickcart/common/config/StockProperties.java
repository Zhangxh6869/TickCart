package com.tickcart.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 库存相关配置，对应 {@code tickcart.stock.*}。
 *
 * <p>只放「全局兜底值」：分类上没配 {@code stock_warn_threshold} 时用它。
 * 真正的有效阈值由 {@code StockWarnService} 沿分类树解析，不要在这里做继承。</p>
 */
@Component
@ConfigurationProperties(prefix = "tickcart.stock")
public class StockProperties {

    /**
     * 库存预警线默认值：库存低于该值在后台标记「告急」。
     *
     * <p>默认 10 是给演示数据定的量级（单品库存多在几十~几百）。真实业务里
     * 更准的做法是「近 N 天日均销量 × 备货天数」，见
     * {@code docs/商品管理界面重设计方案.md} §9.5。</p>
     */
    private int warnThreshold = 10;

    public int getWarnThreshold() {
        return warnThreshold;
    }

    public void setWarnThreshold(int warnThreshold) {
        this.warnThreshold = warnThreshold;
    }
}
