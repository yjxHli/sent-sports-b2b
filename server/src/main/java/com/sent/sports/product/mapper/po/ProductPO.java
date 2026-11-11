package com.sent.sports.product.mapper.po;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDateTime;

/**
 * 商品 SPU 表 {@code product} 持久化对象（P5-11）。
 */
@TableName("product")
public class ProductPO extends BasePO {

    private String productNo;
    private String title;
    private Long categoryId;
    private String brand;
    private String mainImage;
    private String imagesJson;
    private String detailHtml;
    private String paramsJson;
    private Integer canCustomize;
    private String minVisibleLevel;
    private String status;
    private LocalDateTime publishedAt;
    private Long salesCount;
    @TableLogic
    private Integer deleted;

    public String getProductNo() {
        return productNo;
    }

    public void setProductNo(String productNo) {
        this.productNo = productNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getMainImage() {
        return mainImage;
    }

    public void setMainImage(String mainImage) {
        this.mainImage = mainImage;
    }

    public String getImagesJson() {
        return imagesJson;
    }

    public void setImagesJson(String imagesJson) {
        this.imagesJson = imagesJson;
    }

    public String getDetailHtml() {
        return detailHtml;
    }

    public void setDetailHtml(String detailHtml) {
        this.detailHtml = detailHtml;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
    }

    public Integer getCanCustomize() {
        return canCustomize;
    }

    public void setCanCustomize(Integer canCustomize) {
        this.canCustomize = canCustomize;
    }

    public String getMinVisibleLevel() {
        return minVisibleLevel;
    }

    public void setMinVisibleLevel(String minVisibleLevel) {
        this.minVisibleLevel = minVisibleLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getSalesCount() {
        return salesCount;
    }

    public void setSalesCount(Long salesCount) {
        this.salesCount = salesCount;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
