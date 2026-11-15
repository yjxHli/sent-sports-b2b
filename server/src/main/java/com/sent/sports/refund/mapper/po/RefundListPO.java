package com.sent.sports.refund.mapper.po;

import java.time.LocalDateTime;

/**
 * 用户端「我的售后」列表行投影（refund_order 左联 orders 取订单号）。
 * 只读 DTO，不参与写库，故不带 {@code @TableName}。
 */
public class RefundListPO {

    private String refundNo;
    private String orderNo;
    private String status;
    private Long amountCent;
    private LocalDateTime createdAt;
    /** 退款原因（audit_remark → remark → reason_code 依次回退），售后卡片要直接给用户看。 */
    private String reason;
    /** 该订单第一件商品的快照名 + 总件数：售后页按淘宝版式展示商品信息。 */
    private String itemName;
    private Integer itemQty;
    /** 第一件商品对应的 SPU，供「查看商品」跳转（拿 skuId 拼详情页会落到「已下架」）。 */
    private Long productId;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Integer getItemQty() {
        return itemQty;
    }

    public void setItemQty(Integer itemQty) {
        this.itemQty = itemQty;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
