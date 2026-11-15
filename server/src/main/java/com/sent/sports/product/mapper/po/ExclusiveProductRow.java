package com.sent.sports.product.mapper.po;

/**
 * 会员专属商品投影（2026-11-16）。
 *
 * <p>两个来源合成一张列表：</p>
 * <ul>
 *   <li>{@code AGREEMENT}：该客户在一客一价表 {@code customer_sku_price} 里有专属价的商品，
 *       价格取专属价（往往低于零售价，是「会员专属」最硬的证据）；</li>
 *   <li>{@code LEVEL}：商品配了 {@code min_visible_level} 且当前等级达到门槛，
 *       价格取最低在售 SKU 的零售价。</li>
 * </ul>
 */
public class ExclusiveProductRow {

    private Long productId;
    private String productName;
    private String mainImage;
    private Long priceCent;
    /** 等级门槛（AGREEMENT 行为空）。 */
    private String minLevel;
    /** AGREEMENT / LEVEL */
    private String source;

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

    public String getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(String minLevel) {
        this.minLevel = minLevel;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
