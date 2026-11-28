package com.sent.sports.review.mapper.po;

import java.time.LocalDateTime;

/**
 * 评价中心列表行（读面）：我写过的评价 + 商品信息 + 单条浏览/获赞数。
 *
 * <p>商品标题与主图随行 LEFT JOIN 带出：评价中心的卡片要能直接显示「评的是哪个商品」，
 * 逐条回查商品表会变成 N+1。</p>
 */
public class MyReviewRow {

    private Long id;
    private Long productId;
    private String productTitle;
    private String mainImage;
    private Integer rating;
    private String content;
    private String imagesJson;
    private Integer isAnonymous;
    /** NORMAL / HIDDEN（被运营隐藏的评价仍然属于「我写过的」，列表要能看到并标记）。 */
    private String status;
    private Integer viewCount;
    private Integer likeCount;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
