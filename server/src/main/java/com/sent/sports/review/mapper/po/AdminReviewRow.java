package com.sent.sports.review.mapper.po;

import java.time.LocalDateTime;

/**
 * 管理端评价列表行（V56）：评价 + 商品标题 + 评价人昵称（联查投影）。
 * <p>为什么不复用 {@link ReviewRow}：用户端只需要「星级/正文/图片/昵称」，
 * 治理页还要商品标题与状态，多联一张 {@code product} 表。两张投影分开，
 * 免得用户端列表为了一个只有管理端用的字段多一次 JOIN。</p>
 */
public class AdminReviewRow {

    private Long reviewId;
    private Long productId;
    private String productTitle;
    private Integer rating;
    private String content;
    private String imagesJson;
    private Integer isAnonymous;
    private String nickname;
    /** NORMAL / HIDDEN（V56）。 */
    private String status;
    private LocalDateTime createdAt;

    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
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

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImagesJson() {
        return imagesJson;
    }

    public void setImagesJson(String imagesJson) {
        this.imagesJson = imagesJson;
    }

    public Integer getIsAnonymous() {
        return isAnonymous;
    }

    public void setIsAnonymous(Integer isAnonymous) {
        this.isAnonymous = isAnonymous;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
