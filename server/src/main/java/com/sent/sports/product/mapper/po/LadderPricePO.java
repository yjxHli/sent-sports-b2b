package com.sent.sports.product.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 阶梯价表 {@code ladder_price} 持久化对象（P5-11）。
 * <p>{@code [minQty, maxQty]} 闭区间，{@code maxQty} 为空表示「以上」。
 */
@TableName("ladder_price")
public class LadderPricePO extends BasePO {

    private Long skuId;
    private Integer minQty;
    private Integer maxQty;
    private Long priceCent;

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Integer getMinQty() {
        return minQty;
    }

    public void setMinQty(Integer minQty) {
        this.minQty = minQty;
    }

    public Integer getMaxQty() {
        return maxQty;
    }

    public void setMaxQty(Integer maxQty) {
        this.maxQty = maxQty;
    }

    public Long getPriceCent() {
        return priceCent;
    }

    public void setPriceCent(Long priceCent) {
        this.priceCent = priceCent;
    }
}
