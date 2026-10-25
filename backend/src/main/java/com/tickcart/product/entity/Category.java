package com.tickcart.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 商品分类实体，对应 {@code product_category}（树形结构）。
 */
@TableName("product_category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父分类ID，0 为顶级 */
    private Long parentId;

    private String name;

    private String icon;

    /** 层级 1一级 2二级... */
    private Integer level;

    /** 排序值（越小越靠前） */
    private Integer sort;

    /** 状态 0禁用 1启用 */
    private Integer status;

    /**
     * 库存预警线：库存低于该值在后台标记「告急」。
     *
     * <p><b>null 有明确语义 —— 表示「没配过」，继承父分类、最终继承全局配置</b>
     * （{@code tickcart.stock.warn-threshold}）。所以这里不能用基本类型，
     * 也不能在库里给 DEFAULT 值，否则「继承」和「恰好等于默认值」会被混成一种状态。</p>
     *
     * <p><b>★ updateStrategy = ALWAYS 不能省。</b>MyBatis-Plus 默认策略是 NOT_NULL，
     * 会把值为 null 的字段从 UPDATE 的 SET 里剔掉 —— 结果是「清空阈值改回继承」这条
     * 请求返回 success、库里却纹丝不动（实测过：传 null 后仍是 8）。
     * 这个字段每次更新都必须写进去，null 才是「改回继承」的有效意图。</p>
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer stockWarnThreshold;

    @TableLogic
    private Integer deleted;

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

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getStockWarnThreshold() {
        return stockWarnThreshold;
    }

    public void setStockWarnThreshold(Integer stockWarnThreshold) {
        this.stockWarnThreshold = stockWarnThreshold;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
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
