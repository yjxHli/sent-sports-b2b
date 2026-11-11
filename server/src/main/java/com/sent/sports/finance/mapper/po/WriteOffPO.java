package com.sent.sports.finance.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 核销明细 {@code write_off} 持久化对象（P5-21）。
 * <p>一次核销可能分摊到多笔应收，故以 {@code writeOffNo} 把多行归为一组（避免为一条头记录再建一张表）；
 * 反查时按该号分组还原 {@code WriteOff} 聚合。
 */
@TableName("write_off")
public class WriteOffPO extends BasePO {

    private String writeOffNo;
    private String paymentRecordNo;
    private Long receivableId;
    private Long customerId;
    private Long amountCent;
    private Long operatorId;

    public String getWriteOffNo() {
        return writeOffNo;
    }

    public void setWriteOffNo(String writeOffNo) {
        this.writeOffNo = writeOffNo;
    }

    public String getPaymentRecordNo() {
        return paymentRecordNo;
    }

    public void setPaymentRecordNo(String paymentRecordNo) {
        this.paymentRecordNo = paymentRecordNo;
    }

    public Long getReceivableId() {
        return receivableId;
    }

    public void setReceivableId(Long receivableId) {
        this.receivableId = receivableId;
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

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }
}
