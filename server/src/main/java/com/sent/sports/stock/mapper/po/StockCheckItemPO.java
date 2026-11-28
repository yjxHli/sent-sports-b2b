package com.sent.sports.stock.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 盘点单行 {@code stock_check_item} 持久化对象（V5 建表，2026-11-28 起真正启用）。
 *
 * <p>一次盘点针对一个 SKU，故一行就是一单明细；{@code uk_check_sku(check_id, sku_id)} 是
 * {@code ON DUPLICATE KEY UPDATE} 的落点，保证反复 save 同一张盘点单不会插出多行。
 * <p>{@code actualQty/diffQty} 在「还没录实盘」时为 NULL，读取侧还原成领域约定的 -1 / 0。
 */
@TableName("stock_check_item")
public class StockCheckItemPO extends BasePO {

    private Long checkId;
    private Long skuId;
    private Integer bookQty;
    private Integer actualQty;
    private Integer diffQty;

    public Long getCheckId() {
        return checkId;
    }

    public void setCheckId(Long checkId) {
        this.checkId = checkId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Integer getBookQty() {
        return bookQty;
    }

    public void setBookQty(Integer bookQty) {
        this.bookQty = bookQty;
    }

    public Integer getActualQty() {
        return actualQty;
    }

    public void setActualQty(Integer actualQty) {
        this.actualQty = actualQty;
    }

    public Integer getDiffQty() {
        return diffQty;
    }

    public void setDiffQty(Integer diffQty) {
        this.diffQty = diffQty;
    }
}
