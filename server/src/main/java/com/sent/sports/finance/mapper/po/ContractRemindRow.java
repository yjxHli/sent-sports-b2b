package com.sent.sports.finance.mapper.po;

import java.time.LocalDate;

/** 合同到期提醒查询行：合同本体 + 当前提醒档位（V67）。 */
public class ContractRemindRow {

    private Long contractId;
    private Long customerId;
    private String contractNo;
    private Long amountCent;
    private LocalDate expireDate;
    private Integer remindLevel;

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

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

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }

    public LocalDate getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(LocalDate expireDate) {
        this.expireDate = expireDate;
    }

    public Integer getRemindLevel() {
        return remindLevel;
    }

    public void setRemindLevel(Integer remindLevel) {
        this.remindLevel = remindLevel;
    }
}
