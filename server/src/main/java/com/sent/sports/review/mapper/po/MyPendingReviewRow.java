package com.sent.sports.review.mapper.po;

import java.time.LocalDateTime;

/**
 * 评价中心「待评价」行（读面）：已完成订单里、该商品还没写过评价的那一行。
 *
 * <p>字段全部来自订单侧联查（订单头 + 明细 + SKU/SPU），
 * 这样评价中心不必为了拼「待评价」再调一次订单接口，也不会出现两套「已完成」口径。</p>
 */
public class MyPendingReviewRow {

    private String orderNo;
    private Long productId;
    private String productTitle;
    private String mainImage;
    private Integer qty;
    /** 实付（该行金额 - 分摊优惠，单位分） */
    private Long paidCent;
    private LocalDateTime completedAt;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public String getMainImage() {
        return mainImage;
    }

    public void setMainImage(String mainImage) {
        this.mainImage = mainImage;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Long getPaidCent() {
        return paidCent;
    }

    public void setPaidCent(Long paidCent) {
        this.paidCent = paidCent;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
