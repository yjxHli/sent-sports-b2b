package com.sent.sports.finance.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.finance.dto.admin.AdminFinanceDtos;
import com.sent.sports.finance.service.CreditGrantUseCase;
import com.sent.sports.finance.service.EnterpriseAuditUseCase;
import com.sent.sports.finance.service.FinanceQueryService;
import com.sent.sports.finance.service.InvoicePdfService;
import com.sent.sports.finance.service.PaymentRecordUseCase;
import com.sent.sports.finance.service.ReverseRecordUseCase;
import com.sent.sports.finance.service.WriteOffUseCase;
import com.sent.sports.finance.service.dto.CreditGrantCmd;
import com.sent.sports.finance.service.dto.CreditGrantResult;
import com.sent.sports.finance.service.dto.EnterpriseAuditResult;
import com.sent.sports.finance.service.dto.InvoicePdfFile;
import com.sent.sports.finance.service.dto.PaymentRecordCmd;
import com.sent.sports.finance.service.dto.PaymentRecordResult;
import com.sent.sports.finance.service.dto.ReverseCmd;
import com.sent.sports.finance.service.dto.ReverseResult;
import com.sent.sports.finance.service.dto.WriteOffCmd;
import com.sent.sports.finance.service.dto.WriteOffLine;
import com.sent.sports.finance.service.dto.WriteOffResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 财务控制器（P4-49）：应收台账 / 登记回款 / 核销 / 冲销 / 授信 / 发票（plan §8.12）。
 * <p>依赖同模块用例（{@link PaymentRecordUseCase} / {@link WriteOffUseCase} /
 * {@link ReverseRecordUseCase} / {@link CreditGrantUseCase}）与查询门面 {@link FinanceQueryService}
 * （桩，P5 接入仓储）。操作人绑定（@CurrentUserArg）在 Phase 5，现以占位常量传入用例。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminFinanceController {

    private final PaymentRecordUseCase paymentRecordUseCase;
    private final WriteOffUseCase writeOffUseCase;
    private final ReverseRecordUseCase reverseRecordUseCase;
    private final CreditGrantUseCase creditGrantUseCase;
    private final EnterpriseAuditUseCase enterpriseAuditUseCase;
    private final FinanceQueryService financeQueryService;
    private final InvoicePdfService invoicePdfService;

    /** P5 绑定当前用户：阶段占位操作人 ID。 */
    private static final long OPERATOR_PLACEHOLDER = 0L;

    public AdminFinanceController(PaymentRecordUseCase paymentRecordUseCase,
                                 WriteOffUseCase writeOffUseCase,
                                 ReverseRecordUseCase reverseRecordUseCase,
                                 CreditGrantUseCase creditGrantUseCase,
                                 EnterpriseAuditUseCase enterpriseAuditUseCase,
                                 FinanceQueryService financeQueryService,
                                 InvoicePdfService invoicePdfService) {
        this.paymentRecordUseCase = paymentRecordUseCase;
        this.writeOffUseCase = writeOffUseCase;
        this.reverseRecordUseCase = reverseRecordUseCase;
        this.creditGrantUseCase = creditGrantUseCase;
        this.enterpriseAuditUseCase = enterpriseAuditUseCase;
        this.financeQueryService = financeQueryService;
        this.invoicePdfService = invoicePdfService;
    }

    @GetMapping("/receivables")
    public R<PageResult<AdminFinanceDtos.ReceivableVO>> receivables(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean openOnly) {
        return R.ok(financeQueryService.receivables(page, customerId, status, openOnly));
    }

    /** 发票申请列表（收口阶段）：真实分页，票面金额联查订单应付金额。 */
    @GetMapping("/invoices")
    public R<PageResult<AdminFinanceDtos.InvoiceVO>> invoices(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) String status) {
        return R.ok(financeQueryService.invoices(page, status));
    }

    /**
     * 按订单号新建开票申请并开具：发票列表为空、没有申请记录时也能开票，
     * 不再依赖前端写死的 invoiceId=1。
     */
    @PostMapping("/invoices")
    @RequirePerm("finance:invoice")
    public R<AdminFinanceDtos.InvoiceIssueVO> applyInvoice(
            @Valid @RequestBody AdminFinanceDtos.InvoiceApplyCmd cmd) {
        return R.ok(financeQueryService.applyInvoice(cmd));
    }

    /**
     * 发票 PDF（增值税电子普通发票版式）。
     * <p>默认 {@code inline} 供前端 iframe 预览；带 {@code download=true} 时改
     * {@code attachment} 触发浏览器下载。</p>
     * <p>文件名必须分成两段：{@code filename=} 只能是 ASCII（Tomcat 会直接丢弃含
     * 非 ISO-8859-1 字符的响应头，中文名塞进去会导致整个 Content-Disposition 消失），
     * 真正的中文名放在 RFC 5987 的 {@code filename*=UTF-8''...} 里。</p>
     */
    @GetMapping("/invoices/{invoiceId}/pdf")
    public ResponseEntity<byte[]> invoicePdf(
            @PathVariable long invoiceId,
            @RequestParam(required = false, defaultValue = "false") boolean download) {
        InvoicePdfFile file = invoicePdfService.render(invoiceId);
        String disposition = (download ? "attachment" : "inline")
                + "; filename=\"invoice-" + invoiceId + ".pdf\""
                + "; filename*=UTF-8''" + rfc5987(file.fileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }

    /** RFC 5987 编码：URLEncoder 把空格编成 +，但 header 里必须还原成 %20。 */
    private static String rfc5987(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 合同列表（收口阶段）：真实分页，到期日换算成毫秒供前端计算剩余天数。 */
    @GetMapping("/contracts")
    public R<PageResult<AdminFinanceDtos.ContractVO>> contracts(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dueState) {
        return R.ok(financeQueryService.contracts(page, status, dueState));
    }

    /** 回款列表（收口阶段）：真分页，可按客户 / 状态过滤，页面不再只显示本次登记结果。 */
    @GetMapping("/payment-records")
    public R<PageResult<AdminFinanceDtos.PaymentRecordVO>> paymentRecords(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status) {
        return R.ok(financeQueryService.paymentRecords(page, customerId, status));
    }

    @PostMapping("/payment-records")
    @RequirePerm("finance:record")
    public R<AdminFinanceDtos.RecordVO> record(@Valid @RequestBody AdminFinanceDtos.RecordCmd cmd) {
        PaymentRecordResult r = paymentRecordUseCase.record(new PaymentRecordCmd(
                cmd.customerId(), cmd.amountCent(), cmd.serialNo(), OPERATOR_PLACEHOLDER, cmd.remark()));
        return R.ok(new AdminFinanceDtos.RecordVO(r.paymentNo(), r.customerId(), r.amountCent(),
                r.serialNo(), r.status(), r.createdAtMillis(), r.remark()));
    }

    @PostMapping("/payment-records/{paymentNo}/write-off")
    @RequirePerm("finance:writeoff")
    public R<AdminFinanceDtos.WriteOffVO> writeOff(@PathVariable String paymentNo,
                                                   @Valid @RequestBody AdminFinanceDtos.WriteOffCmd cmd) {
        List<WriteOffLine> lines = cmd.lines().stream()
                .map(l -> new WriteOffLine(l.receivableId(), l.amount()))
                .toList();
        WriteOffResult r = writeOffUseCase.writeOff(new WriteOffCmd(
                cmd.customerId(), paymentNo, lines, OPERATOR_PLACEHOLDER));
        return R.ok(new AdminFinanceDtos.WriteOffVO(r.paymentNo(), r.totalWrittenOff(), r.closedReceivableIds(), r.status()));
    }

    @PostMapping("/payment-records/{paymentNo}/reverse")
    @RequirePerm("finance:reverse")
    public R<AdminFinanceDtos.ReverseVO> reverse(@PathVariable String paymentNo,
                                                 @Valid @RequestBody AdminFinanceDtos.ReverseCmd cmd) {
        ReverseResult r = reverseRecordUseCase.reverse(
                new ReverseCmd(paymentNo, OPERATOR_PLACEHOLDER, cmd.reason()));
        return R.ok(new AdminFinanceDtos.ReverseVO(r.paymentNo(), r.status()));
    }

    @PostMapping("/enterprise/{id}/audit")
    @RequirePerm("customer:manage")
    public R<AdminFinanceDtos.EnterpriseAuditVO> auditEnterprise(
            @PathVariable long id,
            @Valid @RequestBody AdminFinanceDtos.EnterpriseAuditCmd cmd) {
        EnterpriseAuditResult r = enterpriseAuditUseCase.audit(id, Boolean.TRUE.equals(cmd.approved()),
                cmd.remark());
        return R.ok(new AdminFinanceDtos.EnterpriseAuditVO(r.customerId(), r.status(), r.approved()));
    }

    @PostMapping("/enterprise/{id}/credit")
    @RequirePerm("finance:credit")
    public R<AdminFinanceDtos.CreditVO> credit(@PathVariable long id,
                                               @Valid @RequestBody AdminFinanceDtos.CreditCmd cmd) {
        CreditGrantResult r = creditGrantUseCase.grant(new CreditGrantCmd(
                id, cmd.totalCent(), cmd.accountPeriodDays(), cmd.validFromMillis(),
                cmd.validToMillis(), OPERATOR_PLACEHOLDER, cmd.remark()));
        return R.ok(new AdminFinanceDtos.CreditVO(r.customerId(), r.totalCent(), r.availableCent(),
                r.validToMillis(), r.status()));
    }

    @PostMapping("/invoices/{invoiceId}/issue")
    @RequirePerm("finance:invoice")
    public R<Object> issueInvoice(@PathVariable long invoiceId,
                                 @RequestBody AdminFinanceDtos.InvoiceCmd cmd) {
        return R.ok(financeQueryService.issueInvoice(invoiceId, cmd.pdfUrl(), cmd.remark()));
    }
}
