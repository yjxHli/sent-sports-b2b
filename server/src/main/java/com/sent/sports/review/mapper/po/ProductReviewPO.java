package com.sent.sports.review.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 商品评价持久化对象（V55）。
 * <p>只承载行形状：星级区间、内容长度、图片张数等规则全部在 service 层校验，
 * 这样规则变更不必动表结构，domain 也不必感知 MyBatis。</p>
 */
@TableName("product_review")
public class ProductReviewPO extends BasePO {

    private Long productId;
    private Long skuId;
    private String orderNo;
    private Long customerId;
    private Integer rating;
    private String content;
    /** 文件服务 fileId 数组的 JSON 文本，读写统一走 {@code JsonListCodec}。 */
    private String imagesJson;
    /** 1=匿名展示；对外 VO 不落这个原始值，只落 {@code anonymous}。 */
    private Integer isAnonymous;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImagesJson() {
        return imagesJson;
    }

    public void setImagesJson(String imagesJson) {
        this.imagesJson = imagesJson;
    }

    public Integer getIsAnonymous() {
        return isAnonymous;
    }

    public void setIsAnonymous(Integer isAnonymous) {
        this.isAnonymous = isAnonymous;
    }
}
