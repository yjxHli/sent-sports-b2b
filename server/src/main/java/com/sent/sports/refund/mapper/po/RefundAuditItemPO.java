package com.sent.sports.refund.mapper.po;

import java.time.LocalDateTime;

/**
 * 管理端退款审核列表行（联查投影）：{@code refund_order} 左联 {@code orders} 取订单号。
 * <p>字段与前端 {@code RefundVO} 一致：refundNo / orderNo / status / amountCent / reason / createdAt。</p>
 */
public class RefundAuditItemPO {

    private String refundNo;
    private String orderNo;
    private String status;
    private Long amountCent;
    private String reason;
    /** 用户申请时上传的凭证图片（JSON 数组原文），由查询层解码成 fileId 列表后再出参。 */
    private String imagesJson;
    /** 是否需客服人工复核（V52）：1 = 订单含「审核通过未排产」的定制单。 */
    private Integer needManualReview;
    private LocalDateTime createdAt;

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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getImagesJson() {
        return imagesJson;
    }

    public void setImagesJson(String imagesJson) {
        this.imagesJson = imagesJson;
    }

    public Integer getNeedManualReview() {
        return needManualReview;
    }

    public void setNeedManualReview(Integer needManualReview) {
        this.needManualReview = needManualReview;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
