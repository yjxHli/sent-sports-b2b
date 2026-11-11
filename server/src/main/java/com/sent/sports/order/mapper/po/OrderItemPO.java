package com.sent.sports.order.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 订单行 {@code order_item} 持久化对象（P5-13）。
 * <p>{@code shippedQty} 是「已发数量」，发货时以条件更新累加（{@code shippedQty + qty <= qty} 防超发）。
 */
@TableName("order_item")
public class OrderItemPO extends BasePO {

    private Long orderId;
    private Long skuId;
    private String spuTitleSnap;
    private String specSnap;
    private Integer qty;
    private Long unitPriceCent;
    private String priceSource;
    private String priceRuleId;
    private Long amountCent;
    private Long discountAllocCent;
    private Integer shippedQty;
    private Integer customFlag;
    private Long customFeeCent;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

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

    public String getPriceSource() {
        return priceSource;
    }

    public void setPriceSource(String priceSource) {
        this.priceSource = priceSource;
    }

    public String getPriceRuleId() {
        return priceRuleId;
    }

    public void setPriceRuleId(String priceRuleId) {
        this.priceRuleId = priceRuleId;
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

    public Integer getShippedQty() {
        return shippedQty;
    }

    public void setShippedQty(Integer shippedQty) {
        this.shippedQty = shippedQty;
    }

    public Integer getCustomFlag() {
        return customFlag;
    }

    public void setCustomFlag(Integer customFlag) {
        this.customFlag = customFlag;
    }

    public Long getCustomFeeCent() {
        return customFeeCent;
    }

    public void setCustomFeeCent(Long customFeeCent) {
        this.customFeeCent = customFeeCent;
    }
}
