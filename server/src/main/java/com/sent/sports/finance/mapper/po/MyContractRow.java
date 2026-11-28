package com.sent.sports.finance.mapper.po;

import java.time.LocalDate;

/**
 * 用户端「我的合同」行：只暴露合同号、金额、状态、起止日期与折扣率。
 * <p>合同文件地址（{@code file_url}）不在此投影内——演示环境的地址多为占位值，
 * 直接透出会让用户点到 404。</p>
 */
public class MyContractRow {

    private Long contractId;
    private String contractNo;
    private Long amountCent;
    private String status;
    private LocalDate signDate;
    private LocalDate expireDate;
    private Integer discountRate;

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public String getContractNo() {
        return contractNo;
    }

    public void setContractNo(String contractNo) {
        this.contractNo = contractNo;
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
}
