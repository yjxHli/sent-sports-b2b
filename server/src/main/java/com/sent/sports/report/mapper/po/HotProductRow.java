package com.sent.sports.report.mapper.po;

/**
 * 热销商品聚合的只读投影（无 {@code @TableName}，不写库）。
 *
 * <p>{@code skuName} 优先取订单行快照 {@code spu_title_snap}（历史订单即使商品改名也保持原貌），
 * 快照为空时回落 {@code product.title}，再为空由 SQL 兜底「商品#{skuId}」。</p>
 */
public class HotProductRow {

    private Long skuId;
    private String skuName;
    private String spec;
    private Long qty;
    private Long amountCent;
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

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }
}
