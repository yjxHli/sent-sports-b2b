package com.sent.sports.finance.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDate;

/**
 * 合同 {@code contract} 持久化对象（收口阶段）。
 * <p>{@code amountCent/status} 由 V10 迁移补列，存量行默认 0 / ACTIVE，需业务回填；
 * 到期日仍以 {@code expireDate} 为准，前端按该值计算剩余天数。</p>
 */
@TableName("contract")
public class ContractPO extends BasePO {

    private Long customerId;
    private String contractNo;
    private LocalDate signDate;
    private LocalDate expireDate;
    private Integer discountRate;
    private String fileUrl;
    private Long amountCent;
    private String status;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getContractNo() {
        return contractNo;
    }

    public void setContractNo(String contractNo) {
        this.contractNo = contractNo;
    }

    public LocalDate getSignDate() {
        return signDate;
    }

    public void setSignDate(LocalDate signDate) {
        this.signDate = signDate;
    }

    public LocalDate getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(LocalDate expireDate) {
        this.expireDate = expireDate;
    }

    public Integer getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(Integer discountRate) {
        this.discountRate = discountRate;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
