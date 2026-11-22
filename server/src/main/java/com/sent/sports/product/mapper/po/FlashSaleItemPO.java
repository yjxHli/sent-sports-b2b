package com.sent.sports.product.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 秒杀活动商品表 {@code flash_sale_item}（V54）。
 * <p>一个活动下同一 SKU 只允许一行（唯一键 {@code uk_flash_activity_sku}）。</p>
 */
@TableName("flash_sale_item")
public class FlashSaleItemPO extends BasePO {

    private Long activityId;
    private Long skuId;
    private Long flashPriceCent;
    private Integer sort;

    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Long getFlashPriceCent() { return flashPriceCent; }
    public void setFlashPriceCent(Long flashPriceCent) { this.flashPriceCent = flashPriceCent; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
