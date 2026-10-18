package com.tickcart.common.idempotent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 幂等记录实体，对应 {@code idempotent_record}。
 *
 * <p>唯一键 {@code (scene, user_id, idem_key)} 是整套幂等的支点：
 * 插入成功即代表抢到执行权，撞键即代表重复请求。</p>
 *
 * <p><b>为什么没有 status</b>：记录与业务同事务写入，事务未提交时该行对其他事务不可见；
 * 业务失败则整体回滚、记录消失。所以任何可见的行都必然已完成（{@code bizNo} 非空），
 * 再加状态字段会变成永远写不到的死字段。</p>
 */
@TableName("idempotent_record")
public class IdempotentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务场景，见 {@code IdempotentService.SCENE_*} */
    private String scene;

    private Long userId;

    private String idemKey;

    /** 业务单号；非空即代表该次请求已成功完成 */
    private String bizNo;

    /** 过期时间，定时任务按此清理 */
    private LocalDateTime expireAt;

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

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public void setIdemKey(String idemKey) {
        this.idemKey = idemKey;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
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
