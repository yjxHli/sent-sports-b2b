package com.sent.sports.customize.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 定制单 {@code custom_order} 持久化对象（P5-16）。
 * <p>{@code orderItemId} 上唯一索引保证「一个订单行只能挂一张定制单」，杜绝对同一行重复收定制费。
 */
@TableName("custom_order")
public class CustomOrderPO extends BasePO {

    private String customNo;
    private Long orderItemId;
    private Long customerId;
    private String serviceType;
    private String printSide;
    private Integer qty;
    private Long feeCent;
    private String status;
    private Integer currentVersion;
    private Integer rejectCount;
    /** V13：审核通过时锁定的图稿版本，重传图稿后不可回退。 */
    private Integer lockedVersion;
    private LocalDateTime reviewDeadline;
    private Long reviewerId;
    private LocalDateTime productionStartAt;
    private LocalDateTime finishedAt;
    private LocalDate expectShipDate;
    private String remark;

    public String getCustomNo() {
        return customNo;
    }

    public void setCustomNo(String customNo) {
        this.customNo = customNo;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getPrintSide() {
        return printSide;
    }

    public void setPrintSide(String printSide) {
        this.printSide = printSide;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Long getFeeCent() {
        return feeCent;
    }

    public void setFeeCent(Long feeCent) {
        this.feeCent = feeCent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public Integer getRejectCount() {
        return rejectCount;
    }

    public void setRejectCount(Integer rejectCount) {
        this.rejectCount = rejectCount;
    }

    public Integer getLockedVersion() {
        return lockedVersion;
    }

    public void setLockedVersion(Integer lockedVersion) {
        this.lockedVersion = lockedVersion;
    }

    public LocalDateTime getReviewDeadline() {
        return reviewDeadline;
    }

    public void setReviewDeadline(LocalDateTime reviewDeadline) {
        this.reviewDeadline = reviewDeadline;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public LocalDateTime getProductionStartAt() {
        return productionStartAt;
    }

    public void setProductionStartAt(LocalDateTime productionStartAt) {
        this.productionStartAt = productionStartAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public LocalDate getExpectShipDate() {
        return expectShipDate;
    }

    public void setExpectShipDate(LocalDate expectShipDate) {
        this.expectShipDate = expectShipDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
