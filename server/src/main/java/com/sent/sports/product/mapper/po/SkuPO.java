package com.sent.sports.product.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * SKU 表 {@code sku} 持久化对象（P5-11）。
 * <p>{@code floorPriceCent} 是改价下限：{@code BELOW_FLOOR_PRICE(40920)} 由定价领域按此校验。
 */
@TableName("sku")
public class SkuPO extends BasePO {

    private Long spuId;
    private String skuNo;
    private String barcode;
    private String specJson;
    private Long retailPriceCent;
    private Long floorPriceCent;
    private Long costPriceCent;
    private Integer weightG;
    private String status;

    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }

    public String getSkuNo() {
        return skuNo;
    }

    public void setSkuNo(String skuNo) {
        this.skuNo = skuNo;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getSpecJson() {
        return specJson;
    }

    public void setSpecJson(String specJson) {
        this.specJson = specJson;
    }

    public Long getRetailPriceCent() {
        return retailPriceCent;
    }

    public void setRetailPriceCent(Long retailPriceCent) {
        this.retailPriceCent = retailPriceCent;
    }

    public Long getFloorPriceCent() {
        return floorPriceCent;
    }

    public void setFloorPriceCent(Long floorPriceCent) {
        this.floorPriceCent = floorPriceCent;
    }

    public Long getCostPriceCent() {
        return costPriceCent;
    }

    public void setCostPriceCent(Long costPriceCent) {
        this.costPriceCent = costPriceCent;
    }

    public Integer getWeightG() {
        return weightG;
    }

    public void setWeightG(Integer weightG) {
        this.weightG = weightG;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
