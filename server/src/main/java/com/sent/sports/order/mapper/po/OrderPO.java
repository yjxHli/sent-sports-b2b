package com.sent.sports.order.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDateTime;

/**
 * 订单主表 {@code orders} 持久化对象（P5-13）。
 * <p>{@code idemKey} 对应唯一索引 {@code uk_order_idem}：客户端重复提交时靠数据库唯一键兜底，
 * 而不是靠先查后插（并发下先查后插必然漏判）。
 */
@TableName("orders")
public class OrderPO extends BasePO {

    private String orderNo;
    private Long customerId;
    private String customerType;
    private Long ownerStaffId;
    private String status;
    private String payStatus;
    private String customStatus;
    private String afterSaleStatus;
    private Long goodsAmountCent;
    private Long customFeeCent;
    private Long discountCent;
    private Long freightCent;
    private Long pointDeductCent;
    private Long payableCent;
    private Long paidCent;
    private Long receivedCent;
    private String addressSnapshotJson;
    private Integer invoiceFlag;
    private String remark;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
    private Long version;
    private String idemKey;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public Long getOwnerStaffId() {
        return ownerStaffId;
    }

    public void setOwnerStaffId(Long ownerStaffId) {
        this.ownerStaffId = ownerStaffId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public String getCustomStatus() {
        return customStatus;
    }

    public void setCustomStatus(String customStatus) {
        this.customStatus = customStatus;
    }

    public String getAfterSaleStatus() {
        return afterSaleStatus;
    }

    public void setAfterSaleStatus(String afterSaleStatus) {
        this.afterSaleStatus = afterSaleStatus;
    }

    public Long getGoodsAmountCent() {
        return goodsAmountCent;
    }

    public void setGoodsAmountCent(Long goodsAmountCent) {
        this.goodsAmountCent = goodsAmountCent;
    }

    public Long getCustomFeeCent() {
        return customFeeCent;
    }

    public void setCustomFeeCent(Long customFeeCent) {
        this.customFeeCent = customFeeCent;
    }

    public Long getDiscountCent() {
        return discountCent;
    }

    public void setDiscountCent(Long discountCent) {
        this.discountCent = discountCent;
    }

    public Long getFreightCent() {
        return freightCent;
    }

    public void setFreightCent(Long freightCent) {
        this.freightCent = freightCent;
    }

    public Long getPointDeductCent() {
        return pointDeductCent;
    }

    public void setPointDeductCent(Long pointDeductCent) {
        this.pointDeductCent = pointDeductCent;
    }

    public Long getPayableCent() {
        return payableCent;
    }

    public void setPayableCent(Long payableCent) {
        this.payableCent = payableCent;
    }

    public Long getPaidCent() {
        return paidCent;
    }

    public void setPaidCent(Long paidCent) {
        this.paidCent = paidCent;
    }

    public Long getReceivedCent() {
        return receivedCent;
    }

    public void setReceivedCent(Long receivedCent) {
        this.receivedCent = receivedCent;
    }

    public String getAddressSnapshotJson() {
        return addressSnapshotJson;
    }

    public void setAddressSnapshotJson(String addressSnapshotJson) {
        this.addressSnapshotJson = addressSnapshotJson;
    }

    public Integer getInvoiceFlag() {
        return invoiceFlag;
    }

    public void setInvoiceFlag(Integer invoiceFlag) {
        this.invoiceFlag = invoiceFlag;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public void setIdemKey(String idemKey) {
        this.idemKey = idemKey;
    }
}
