package com.sent.sports.stock.mapper.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * SKU 库存台账 {@code sku_stock} 持久化对象（P5-12）。
 * <p>与其它表不同，本表没有自增 id：<b>主键就是 {@code sku_id}</b>（一 SKU 一行，不做多库位拆分），
 * 因此不继承 {@code BasePO}，主键用 {@code IdType.INPUT} 由调用方指定。
 * <p>可用量 = {@code physicalQty - lockedQty - pendingQty - frozenQty}，由 SQL 侧原子校验，不在 Java 侧算。
 */
@TableName("sku_stock")
public class SkuStockPO {

    @TableId(value = "sku_id", type = IdType.INPUT)
    private Long skuId;

    private String locationCode;
    private Integer physicalQty;
    private Integer lockedQty;
    private Integer pendingQty;
    private Integer frozenQty;
    private Integer safetyQty;
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }

    public Integer getPhysicalQty() {
        return physicalQty;
    }

    public void setPhysicalQty(Integer physicalQty) {
        this.physicalQty = physicalQty;
    }

    public Integer getLockedQty() {
        return lockedQty;
    }

    public void setLockedQty(Integer lockedQty) {
        this.lockedQty = lockedQty;
    }

    public Integer getPendingQty() {
        return pendingQty;
    }

    public void setPendingQty(Integer pendingQty) {
        this.pendingQty = pendingQty;
    }

    public Integer getFrozenQty() {
        return frozenQty;
    }

    public void setFrozenQty(Integer frozenQty) {
        this.frozenQty = frozenQty;
    }

    public Integer getSafetyQty() {
        return safetyQty;
    }

    public void setSafetyQty(Integer safetyQty) {
        this.safetyQty = safetyQty;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
