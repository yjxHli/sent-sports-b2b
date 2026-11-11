package com.sent.sports.finance.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 财务接口 Web 层 DTO（P4-48）。
 * 请求体承载外部入参并带校验注解；响应体暴露给前端。
 * 金额一律以「分」为单位承载；展示换算由前端按 {@code *_cent} 处理（plan §8.1）。
 */
public final class AdminFinanceDtos {

    private AdminFinanceDtos() {
    }

    /** 登记回款请求：客户 + 金额（分）+ 流水号 + 备注。 */
    public record RecordCmd(
            @NotNull(message = "客户不能为空") Long customerId,
            @Positive(message = "回款金额必须大于0") long amountCent,
            @NotBlank(message = "流水号不能为空") String serialNo,
            String remark) {
    }

    /** 核销明细行请求。 */
    public record WriteOffLineCmd(
            @NotNull(message = "应收单不能为空") Long receivableId,
            @NotNull(message = "核销金额不能为空") Long amount) {
    }

    /** 核销请求：客户 + 多笔应收分配。 */
    public record WriteOffCmd(
            @NotNull(message = "客户不能为空") Long customerId,
            @NotNull(message = "核销明细不能为空") List<WriteOffLineCmd> lines) {
    }

    /** 回款冲销请求：原因。 */
    public record ReverseCmd(@NotBlank(message = "冲销原因不能为空") String reason) {
    }

    /** 企业授信请求：总额（分）+ 账期天数 + 有效期 + 备注。 */
    public record CreditCmd(
            @Positive(message = "授信总额必须大于0") long totalCent,
            @NotNull(message = "账期天数不能为空") Long accountPeriodDays,
            @NotNull(message = "有效期起不能为空") Long validFromMillis,
            @NotNull(message = "有效期止不能为空") Long validToMillis,
            String remark) {
    }

    /** 发票开具请求：PDF 地址 + 备注（对已存在的开票申请开票 / 换开）。 */
    public record InvoiceCmd(String pdfUrl, String remark) {
    }

    /**
     * 按订单号新建开票申请并开具（财务代客开票）。
     * <p>系统原本没有任何创建开票申请的入口，{@code invoice_request} 恒为 0 行，
     * 而发票管理弹窗把 invoiceId 写死成 1，提交必然 404。按订单号一次完成「建申请 + 开票」补齐闭环。</p>
     */
    public record InvoiceApplyCmd(
            @NotBlank(message = "订单号不能为空") String orderNo,
            @NotBlank(message = "发票抬头不能为空") String title,
            String taxNo,
            String email,
            /**
             * PDF 地址：留空则由系统按 {@code /api/admin/invoices/{id}/pdf} 生成。
             * 票面已由后端现场渲染（{@code InvoicePdfRenderer}），不再依赖外部文件服务，
             * 因此这一栏不再强制人工填写。
             */
            String pdfUrl,
            String remark) {
    }

    /** 开票结果快照（发票号由申请主键生成，保证唯一且可追溯）。 */
    public record InvoiceIssueVO(long invoiceId, String invoiceNo, String orderNo,
                                 String status, String pdfUrl) {
    }

    /**
     * 发票列表行（对齐 web-admin/src/api/finance.ts 的 InvoiceVO）。
     * 票面金额联查订单应付金额；createdAt 为毫秒时间戳，无数据时为 0。
     */
    public record InvoiceVO(long invoiceId, String invoiceNo, long customerId, long amountCent,
                            String status, String pdfUrl, long createdAt) {
    }

    /**
     * 合同列表行（对齐 web-admin/src/api/finance.ts 的 ContractVO）。
     * validToMillis 由 expire_date 按系统时区换算，无到期日为 0。
     */
    public record ContractVO(long contractId, String contractNo, long customerId, long amountCent,
                             String status, long validToMillis) {
    }

    /** 登记回款响应。 */
    public record RecordVO(String paymentNo, long customerId, long amountCent, String serialNo,
                           String status, long createdAtMillis, String remark) {
    }

    /**
     * 回款列表行：对齐 {@code payment_record} 表，金额一律「分」，
     * 时间按系统时区换算成毫秒；{@code reversedReason} 为冲销原因（未冲销为 null）。
     */
    public record PaymentRecordVO(String paymentNo, long customerId, long amountCent,
                                  long writtenOffCent, String serialNo, String status,
                                  long receivedDateMillis, long createdAtMillis,
                                  String remark, String reversedReason) {
    }

    /**
     * 应收台账行（对齐 web-admin 的 ReceivableVO）：已核销取 {@code received_cent}，
     * {@code dueAt} 为到期日 0 点毫秒时间戳，{@code overdueDays} 为逾期天数（未逾期为 0）。
     */
    public record ReceivableVO(long receivableId, String orderNo, long customerId, long amountCent,
                               long writtenOffCent, long dueAt, String status, int overdueDays) {
    }

    /** 核销响应。 */
    public record WriteOffVO(String paymentNo, long totalWrittenOff, java.util.List<Long> closedReceivableIds, String status) {
    }

    /** 回款冲销响应。 */
    public record ReverseVO(String paymentNo, String status) {
    }

    /** 企业授信响应。 */
    public record CreditVO(long customerId, long totalCent, long availableCent, long validToMillis, String status) {
    }

    /**
     * 企业认证审核请求。
     * <p>{@code approved=true} 通过、{@code false} 驳回；{@code remark} 为审核意见。</p>
     */
    public record EnterpriseAuditCmd(
            @NotNull(message = "审核结论不能为空") Boolean approved,
            String remark) {
    }

    /** 企业认证审核响应：回显落库后的档案状态，前端据此即时刷新标签。 */
    public record EnterpriseAuditVO(long customerId, String status, boolean approved) {
    }
}
