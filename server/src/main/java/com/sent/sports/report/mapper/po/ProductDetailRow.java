package com.sent.sports.report.mapper.po;

/** 商品销售明细聚合的只读投影：成交额、成本、库存、价格来源一次取齐。 */
public class ProductDetailRow {

    private Long skuId;
    private String skuName;
    private String spec;
    private Long qty;
    private Long amountCent;
    private Long costPriceCent;
    private String priceSources;
    private Integer stockQty;
    private Integer orderCount;

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getSkuName() {
        return skuName;
    }

    public void setSkuName(String skuName) {
        this.skuName = skuName;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public Long getQty() {
        return qty;
    }

    public void setQty(Long qty) {
        this.qty = qty;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }

    public Long getCostPriceCent() {
        return costPriceCent;
    }

    public void setCostPriceCent(Long costPriceCent) {
        this.costPriceCent = costPriceCent;
    }

    public String getPriceSources() {
        return priceSources;
    }

    public void setPriceSources(String priceSources) {
        this.priceSources = priceSources;
    }

    public Integer getStockQty() {
        return stockQty;
    }

    public void setStockQty(Integer stockQty) {
        this.stockQty = stockQty;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }
}
