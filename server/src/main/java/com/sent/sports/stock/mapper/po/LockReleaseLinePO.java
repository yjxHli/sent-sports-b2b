package com.sent.sports.stock.mapper.po;

/**
 * 订单预占明细投影（P5-20）：整单释放预占时只需要「SKU + 数量」两列，
 * 故不复用订单模块的 PO，避免 stock 模块被订单字段结构牵着走。
 */
public class LockReleaseLinePO {

    private Long skuId;
    private Integer qty;

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
}
