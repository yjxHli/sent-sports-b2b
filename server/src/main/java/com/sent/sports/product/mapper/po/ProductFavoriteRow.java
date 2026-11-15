package com.sent.sports.product.mapper.po;

import java.time.LocalDateTime;

/**
 * 收藏列表投影（{@code product_favorite} 左联 {@code product} 取标题/主图/最低在售价）。
 *
 * <p>只读 DTO，不参与写库，故不继承 {@code BasePO}、也不带 {@code @TableName}。
 * 价格取「在售 SKU 的最低零售价」：一个 SPU 有多个规格时，收藏夹里露出最低价才不容易误导。</p>
 */
public class ProductFavoriteRow {

    private Long productId;
    private String productName;
    private String mainImage;
    private Long priceCent;
    private String productStatus;
    private Integer productDeleted;
    private LocalDateTime favoritedAt;

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

    public LocalDateTime getFavoritedAt() {
        return favoritedAt;
    }

    public void setFavoritedAt(LocalDateTime favoritedAt) {
        this.favoritedAt = favoritedAt;
    }
}
