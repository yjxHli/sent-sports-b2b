package com.sent.sports.finance.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDate;

/**
 * 回款单 {@code payment_record} 持久化对象（P5-21）。
 * <p>{@code recordNo} 是业务号（PaymentRecord.paymentNo），{@code bankSerial} 是银行流水号（唯一性校验用）。
 */
@TableName("payment_record")
public class PaymentRecordPO extends BasePO {

    private String recordNo;
    private Long customerId;
    private Long amountCent;
    private Long writtenOffCent;
    private LocalDate receivedDate;
    private String bankSerial;
    private String remark;
    private String status;
    private String reversedReason;
    private Long operatorId;

    public String getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(String recordNo) {
        this.recordNo = recordNo;
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

    public Long getWrittenOffCent() {
        return writtenOffCent;
    }

    public void setWrittenOffCent(Long writtenOffCent) {
        this.writtenOffCent = writtenOffCent;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDate receivedDate) {
        this.receivedDate = receivedDate;
    }

    public String getBankSerial() {
        return bankSerial;
    }

    public void setBankSerial(String bankSerial) {
        this.bankSerial = bankSerial;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReversedReason() {
        return reversedReason;
    }

    public void setReversedReason(String reversedReason) {
        this.reversedReason = reversedReason;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }
}
