package com.tickcart.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户浏览记录实体，对应 {@code user_view_history}。
 *
 * <p>表上有 {@code uk_user_product} 唯一键且**没有 deleted 列**：
 * 记录浏览是 upsert（重复浏览只刷新 {@code viewTime}），删除记录是物理删除。
 * 不要按逻辑删除理解，也不要给它加 {@code @TableLogic}。</p>
 */
@TableName("user_view_history")
public class UserViewHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 浏览人用户ID */
    private Long userId;

    /** 被浏览的商品ID */
    private Long productId;

    /**
     * 最近一次浏览时间（列表排序依据）。
     *
     * <p>不走 {@code FieldFill.INSERT_UPDATE} 自动填充：写入只发生在 Mapper 的自定义
     * upsert SQL 里（见 {@code UserViewHistoryMapper.upsert}），那条语句直接写 {@code NOW()}。</p>
     */
    private LocalDateTime viewTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public LocalDateTime getViewTime() {
        return viewTime;
    }

    public void setViewTime(LocalDateTime viewTime) {
        this.viewTime = viewTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
