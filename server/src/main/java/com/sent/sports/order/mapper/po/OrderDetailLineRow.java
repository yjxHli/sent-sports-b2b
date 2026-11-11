package com.sent.sports.order.mapper.po;

/**
 * 管理端订单详情行（联查投影，非表对象）：{@code order_item} 左联 {@code custom_order} 取定制状态。
 * <p>列名按驼峰别名书写（{@code map-underscore-to-camel-case: true} 亦可），
 * 字段名与 {@code AdminOrderDtos.AdminOrderLineVO} 一一对应，避免服务层再做字符串映射。</p>
 */
public class OrderDetailLineRow {

    private Long skuId;
    private String skuName;
    private String spec;
    private Integer qty;
    private Long unitPriceCent;
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
