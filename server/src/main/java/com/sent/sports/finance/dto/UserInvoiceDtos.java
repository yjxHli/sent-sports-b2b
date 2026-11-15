package com.sent.sports.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户端开票接口 DTO。
 * <p>{@code type} 取 NORMAL（增值税普通发票）/ SPECIAL（增值税专用发票，需税号）。</p>
 */
public final class UserInvoiceDtos {

    private UserInvoiceDtos() {
    }

    /** 申请开票请求。 */
    public record ApplyCmd(
            @NotBlank(message = "订单号不能为空") String orderNo,
            @NotBlank(message = "发票抬头不能为空") @Size(max = 128, message = "发票抬头过长") String title,
            @Size(max = 64, message = "税号过长") String taxNo,
            /** NORMAL / SPECIAL，缺省按 NORMAL */
            String type,
            @Size(max = 128, message = "邮箱过长") String email) {
    }

    /** 开票申请回执：状态 APPLIED（已提交待开具）/ ISSUED（已开具，可下载 PDF）。 */
    public record InvoiceVO(
            String invoiceId,
            String orderNo,
            String status,
            String title,
            String taxNo,
            String type,
            String email,
            Long amountCent,
            String amountYuan,
            String invoiceNo,
            String pdfUrl,
            Long createdAtMillis) {
    }
}
