package com.sent.sports.product.mapper.po;

import java.time.LocalDateTime;

/**
 * 足迹列表投影（{@code product_footprint} 左联 {@code product}）。
 *
 * <p>比收藏多两个字段：{@code viewCount}（同一商品累计浏览次数）与 {@code lastViewedAt}
 * （列表按它倒序，是「最近浏览」的唯一依据）。</p>
 */
public class ProductFootprintRow {

    private Long productId;
    private String productName;
    private String mainImage;
    private Long priceCent;
    private String productStatus;
    private Integer productDeleted;
    private Integer viewCount;
    private LocalDateTime lastViewedAt;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getMainImage() {
        return mainImage;
    }

    public void setMainImage(String mainImage) {
        this.mainImage = mainImage;
    }

    public Long getPriceCent() {
        return priceCent;
    }

    public void setPriceCent(Long priceCent) {
        this.priceCent = priceCent;
    }

    public String getProductStatus() {
        return productStatus;
    }

    public void setProductStatus(String productStatus) {
        this.productStatus = productStatus;
    }

    public Integer getProductDeleted() {
        return productDeleted;
    }

    public void setProductDeleted(Integer productDeleted) {
        this.productDeleted = productDeleted;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public LocalDateTime getLastViewedAt() {
        return lastViewedAt;
    }

    public void setLastViewedAt(LocalDateTime lastViewedAt) {
        this.lastViewedAt = lastViewedAt;
    }
}
