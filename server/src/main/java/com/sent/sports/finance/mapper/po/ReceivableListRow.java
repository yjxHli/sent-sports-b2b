package com.sent.sports.finance.mapper.po;

import java.time.LocalDate;

/**
 * 管理端应收台账行（联查投影，非表对象）：{@code receivable} 左联 {@code orders} 取订单号。
 * <p>列名与 {@code AdminFinanceDtos.ReceivableVO} / 前端 {@code ReceivableVO} 对齐；
 * 已核销取 {@code receivable.received_cent}（核销时累加，不做二次汇总）。</p>
 */
public class ReceivableListRow {

    private Long receivableId;
    private String orderNo;
    private Long customerId;
    private Long amountCent;
    private Long writtenOffCent;
    private LocalDate dueAt;
    private String status;

    public Long getReceivableId() {
        return receivableId;
    }

    public void setReceivableId(Long receivableId) {
        this.receivableId = receivableId;
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

    public LocalDate getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDate dueAt) {
        this.dueAt = dueAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
