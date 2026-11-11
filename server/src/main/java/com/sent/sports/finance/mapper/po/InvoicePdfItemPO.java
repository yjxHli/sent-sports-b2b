package com.sent.sports.finance.mapper.po;

/**
 * 发票明细读模型（order_item 联查 product 取标题回退）。
 * <p>金额单位为「分」，且是<b>含税</b>金额，税票拆分在 {@code InvoiceTaxCalculator} 里做。</p>
 */
public class InvoicePdfItemPO {

    private Long skuId;
    private String spuTitleSnap;
    private String specSnap;
    private String productTitle;
    private Integer qty;
    private Long unitPriceCent;
    private Long amountCent;

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getSpuTitleSnap() {
        return spuTitleSnap;
    }

    public void setSpuTitleSnap(String spuTitleSnap) {
        this.spuTitleSnap = spuTitleSnap;
    }

    public String getSpecSnap() {
        return specSnap;
    }

    public void setSpecSnap(String specSnap) {
        this.specSnap = specSnap;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Long getUnitPriceCent() {
        return unitPriceCent;
    }

    public void setUnitPriceCent(Long unitPriceCent) {
        this.unitPriceCent = unitPriceCent;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }
}
