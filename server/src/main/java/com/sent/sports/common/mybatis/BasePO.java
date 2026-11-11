package com.sent.sports.common.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import java.time.LocalDateTime;

/**
 * 持久化对象（PO）公共字段（P5-10）。
 * <p>定位：只承载「数据库行」的形状，不含任何业务规则；业务规则一律留在各模块 {@code domain} 层，
 * 二者由仓储实现（P5-19~P5-22）负责转换。这样 domain 保持纯 POJO 可单测，PO 可随表结构自由演进。
 * <p>{@code createdAt/updatedAt} 由 {@code MybatisPlusConfig} 的 {@code MetaObjectHandler} 自动填充；
 * {@code createdBy} 由调用方在业务层写入当前登录人，未登录场景留空。
 */
public abstract class BasePO {

    /** 自增主键（BIGINT UNSIGNED）。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
