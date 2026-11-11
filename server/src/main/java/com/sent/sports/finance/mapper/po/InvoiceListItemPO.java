package com.sent.sports.finance.mapper.po;

import java.time.LocalDateTime;

/**
 * 管理端发票列表行（联查投影，非表对象）：{@code invoice_request} 左联 {@code orders} 取票面金额。
 * <p>列名与 {@code AdminFinanceDtos.InvoiceVO} / 前端 {@code InvoiceVO} 对齐。</p>
 */
public class InvoiceListItemPO {

    private Long invoiceId;
    private String invoiceNo;
    private Long customerId;
    private Long amountCent;
    private String status;
    private String pdfUrl;
    private LocalDateTime createdAt;

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
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

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
