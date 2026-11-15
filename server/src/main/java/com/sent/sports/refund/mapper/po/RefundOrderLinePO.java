package com.sent.sports.refund.mapper.po;

/**
 * 退款审核详情的订单行投影（只读）。
 *
 * <p>与订单详情共用同一套「快照优先、缺失回退当前商品」的取值口径：
 * {@code spu_title_snap/spec_snap} 为空的历史行回退到 sku/product，
 * 这样审核员看到的商品名不会是一列空白。</p>
 */
public class RefundOrderLinePO {

    private Long skuId;
    private String skuName;
    private String spec;
    private Integer qty;
    private Long unitPriceCent;
    private Long amountCent;
    private Long discountAllocCent;
    /** 行实付 = 行金额 − 分摊优惠，退款上限按它算，审核对账也用它对。 */
    private Long linePaidCent;
    private Integer shippedQty;
    private String customStatus;

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

    public Long getDiscountAllocCent() {
        return discountAllocCent;
    }

    public void setDiscountAllocCent(Long discountAllocCent) {
        this.discountAllocCent = discountAllocCent;
    }

    public Long getLinePaidCent() {
        return linePaidCent;
    }

    public void setLinePaidCent(Long linePaidCent) {
        this.linePaidCent = linePaidCent;
    }

    public Integer getShippedQty() {
        return shippedQty;
    }

    public void setShippedQty(Integer shippedQty) {
        this.shippedQty = shippedQty;
    }

    public String getCustomStatus() {
        return customStatus;
    }

    public void setCustomStatus(String customStatus) {
        this.customStatus = customStatus;
    }
}
