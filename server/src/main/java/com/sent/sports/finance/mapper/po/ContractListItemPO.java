package com.sent.sports.finance.mapper.po;

import java.time.LocalDate;

/**
 * 管理端合同列表行（联查投影，非表对象）。
 * <p>{@code validTo} 是 DATE，转换到毫秒由查询实现按系统时区完成。</p>
 */
public class ContractListItemPO {

    private Long contractId;
    private String contractNo;
    private Long customerId;
    private Long amountCent;
    private String status;
    private LocalDate validTo;

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

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
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

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }
}
