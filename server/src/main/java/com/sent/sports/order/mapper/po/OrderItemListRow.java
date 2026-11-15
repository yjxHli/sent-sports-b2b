package com.sent.sports.order.mapper.po;

/**
 * 我的订单列表里的商品行（联查投影，非表对象）。
 *
 * <p>历史订单行的 {@code spu_title_snap/spec_snap} 常常为空（下单聚合只带 SKU 与价格），
 * 因此读侧用 {@code COALESCE} 回退到 {@code sku/product} 的当前值，保证订单卡片不出现空白商品名；
 * 快照存在时仍以快照为准，不会被后来的改名覆盖。</p>
 */
public class OrderItemListRow {

    private Long orderId;
    private Long skuId;
    private String title;
    private String spec;
    private Integer qty;
    private Long unitPriceCent;

    /** 行实付（分）= order_item.amount_cent − discount_alloc_cent，退款按这个值做行上限。 */
    private Long linePaidCent;

    /**
     * 所属商品（SPU）ID：订单卡片点「商品名」要跳商品详情，
     * 只有 skuId 时拼出来的 /product/{skuId} 是死链（商品详情按 productId 查）。
     */
    private Long productId;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public Long getLinePaidCent() {
        return linePaidCent;
    }

    public void setLinePaidCent(Long linePaidCent) {
        this.linePaidCent = linePaidCent;
    }
}
