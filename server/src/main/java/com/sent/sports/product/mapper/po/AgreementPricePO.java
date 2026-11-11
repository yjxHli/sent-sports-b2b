package com.sent.sports.product.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDateTime;

/**
 * 协议价表 {@code agreement_price} 持久化对象（P5-11）。
 * <p>{@code discountRate} 为百分比整数（100 = 不打折）；{@code allowStacking} 决定能否与阶梯价叠加，
 * 叠加规则由定价领域（product/domain/pricing）裁决，PO 只如实落库。
 */
@TableName("agreement_price")
public class AgreementPricePO extends BasePO {

    private Long customerId;
    private Integer discountRate;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Long contractId;
    private Integer allowStacking;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Integer getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(Integer discountRate) {
        this.discountRate = discountRate;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public Integer getAllowStacking() {
        return allowStacking;
    }

    public void setAllowStacking(Integer allowStacking) {
        this.allowStacking = allowStacking;
    }
}
