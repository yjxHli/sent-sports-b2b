package com.sent.sports.web.admin;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.finance.controller.admin.AdminFinanceController;
import com.sent.sports.finance.dto.admin.AdminFinanceDtos;
import com.sent.sports.finance.service.CreditGrantUseCase;
import com.sent.sports.finance.service.EnterpriseAuditUseCase;
import com.sent.sports.finance.service.FinanceQueryService;
import com.sent.sports.finance.service.InvoicePdfService;
import com.sent.sports.finance.service.PaymentRecordUseCase;
import com.sent.sports.finance.service.ReverseRecordUseCase;
import com.sent.sports.finance.service.WriteOffUseCase;
import com.sent.sports.finance.service.dto.CreditGrantResult;
import com.sent.sports.finance.service.dto.EnterpriseAuditResult;
import com.sent.sports.finance.service.dto.InvoicePdfFile;
import com.sent.sports.finance.service.dto.PaymentRecordResult;
import com.sent.sports.finance.service.dto.ReverseResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 财务接口契约测试（P4-47，红→绿）。
 * 登记回款 200、核销超额 40930、冲销查无单 40401、授信 200、应收台账空页 200。
 */
@WebMvcTest(AdminFinanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminFinanceApiTest extends BaseApiTest {

    @MockBean
    private PaymentRecordUseCase paymentRecordUseCase;

    @MockBean
    private WriteOffUseCase writeOffUseCase;

    @MockBean
    private ReverseRecordUseCase reverseRecordUseCase;

    @MockBean
    private CreditGrantUseCase creditGrantUseCase;

    @MockBean
    private FinanceQueryService financeQueryService;

    @MockBean
    private EnterpriseAuditUseCase enterpriseAuditUseCase;

    @MockBean
    private InvoicePdfService invoicePdfService;

    @Test
    @DisplayName("发票 PDF：inline 预览返回 application/pdf，attachemnt 下载带文件名")
    void invoicePdfReturnsPdfBytes() throws Exception {
        byte[] bytes = "%PDF-1.7 demo".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        when(invoicePdfService.render(4L))
                .thenReturn(new InvoicePdfFile(bytes, "发票-INV-4.pdf"));

        mvc.perform(get("/api/admin/invoices/4/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("inline")))
                .andExpect(content().bytes(bytes));

        mvc.perform(get("/api/admin/invoices/4/pdf").param("download", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @DisplayName("发票 PDF：Content-Disposition 必须全 ASCII，否则 Tomcat 会静默丢掉整个响应头")
    void invoicePdfDispositionIsAsciiOnly() throws Exception {
        when(invoicePdfService.render(4L))
                .thenReturn(new InvoicePdfFile(new byte[]{1}, "发票-INV-4.pdf"));

        mvc.perform(get("/api/admin/invoices/4/pdf"))
                .andExpect(status().isOk())
                .andDo(result -> {
                    String disposition = result.getResponse().getHeader("Content-Disposition");
                    org.junit.jupiter.api.Assertions.assertNotNull(disposition,
                            "Content-Disposition 丢失：非 ASCII 文件名会让 Tomcat 丢弃整头");
                    assertTrue(disposition.chars().allMatch(ch -> ch < 128),
                            "存在非 ASCII 字符：" + disposition);
                    assertTrue(disposition.contains("filename*=UTF-8''"), disposition);
                });
    }

    @Test
    @DisplayName("发票 PDF 查无此票：仍按统一 R 结构回 40401，前端能弹出具体原因")
    void invoicePdfNotFoundReturnsR40401() throws Exception {        when(invoicePdfService.render(999L))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "开票申请不存在，无法生成发票 PDF"));

        mvc.perform(get("/api/admin/invoices/999/pdf"))
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("开票申请不存在")));
    }

    @Test
    @DisplayName("企业认证审核通过：返回 ACTIVE，不再降级成系统异常")
    void auditApprovedReturns200() throws Exception {
        when(enterpriseAuditUseCase.audit(91154L, true, "资料齐全"))
                .thenReturn(new EnterpriseAuditResult(91154L, "ACTIVE", true));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("approved", true);
        body.put("remark", "资料齐全");
        mvc.perform(post("/api/admin/enterprise/91154/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customerId").value(91154))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("企业认证驳回：返回 REJECTED")
    void auditRejectedReturns200() throws Exception {
        when(enterpriseAuditUseCase.audit(91154L, false, "资料不完整"))
                .thenReturn(new EnterpriseAuditResult(91154L, "REJECTED", false));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("approved", false);
        body.put("remark", "资料不完整");
        mvc.perform(post("/api/admin/enterprise/91154/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("登记回款成功返回付款单号与金额 200")
    void recordReturns200() throws Exception {
        when(paymentRecordUseCase.record(any())).thenReturn(
                new PaymentRecordResult("PR1", 1L, 50000L, "S1", "PENDING", 1700000000000L, "remark"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", 1L);
        body.put("amountCent", 50000);
        body.put("serialNo", "S1");
        body.put("remark", "remark");
        mvc.perform(post("/api/admin/payment-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.paymentNo").value("PR1"))
                .andExpect(jsonPath("$.data.amountCent").value(50000));
    }

    @Test
    @DisplayName("核销金额超额返回 WRITE_OFF_OVERFLOW 40930")
    void writeOffOverflowReturns40930() throws Exception {
        when(writeOffUseCase.writeOff(any())).thenThrow(new BizException(ErrorCode.WRITE_OFF_OVERFLOW));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", 1L);
        body.put("lines", List.of(Map.of("receivableId", 10L, "amount", 99999L)));
        mvc.perform(post("/api/admin/payment-records/PR1/write-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40930));
    }

    @Test
    @DisplayName("回款冲销查无单返回 NOT_FOUND 40401")
    void reverseNotFoundReturns40401() throws Exception {
        when(reverseRecordUseCase.reverse(any())).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "录错");
        mvc.perform(post("/api/admin/payment-records/PR404/reverse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("企业授信成功返回总额与可用额度 200")
    void creditReturns200() throws Exception {
        when(creditGrantUseCase.grant(any())).thenReturn(
                new CreditGrantResult(2L, 100000L, 100000L, 1893456000000L, "ACTIVE"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalCent", 100000);
        body.put("accountPeriodDays", 30);
        body.put("validFromMillis", 1700000000000L);
        body.put("validToMillis", 1893456000000L);
        body.put("remark", "年度授信");
        mvc.perform(post("/api/admin/enterprise/2/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customerId").value(2))
                .andExpect(jsonPath("$.data.totalCent").value(100000))
                .andExpect(jsonPath("$.data.availableCent").value(100000));
    }

    @Test
    @DisplayName("应收台账按客户/状态筛选返回分页（字段与前端 ReceivableVO 一致）")
    void receivablesReturnsPage() throws Exception {
        AdminFinanceDtos.ReceivableVO vo = new AdminFinanceDtos.ReceivableVO(
                444L, "SO20261107001000", 9002L, 20000L, 0L, 1796140800000L, "NORMAL", 0);
        when(financeQueryService.receivables(any(), eq(9002L), eq("NORMAL"), eq(false))).thenReturn(
                com.sent.sports.common.web.PageResult.of(java.util.List.of(vo), 1L, 1, 10));
        mvc.perform(get("/api/admin/receivables")
                        .param("page", "1").param("size", "10")
                        .param("customerId", "9002").param("status", "NORMAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].orderNo").value("SO20261107001000"))
                .andExpect(jsonPath("$.data.list[0].writtenOffCent").value(0))
                .andExpect(jsonPath("$.data.list[0].status").value("NORMAL"));
    }

    @Test
    @DisplayName("应收台账 openOnly=true 透传到 service（核销工作台只要未结清）")
    void receivablesPassesOpenOnly() throws Exception {
        when(financeQueryService.receivables(any(), eq(9002L), isNull(), eq(true))).thenReturn(
                PageResult.of(List.of(), 0L, 1, 10));

        mvc.perform(get("/api/admin/receivables")
                        .param("page", "1").param("size", "10")
                        .param("customerId", "9002").param("openOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(financeQueryService).receivables(any(), eq(9002L), isNull(), eq(true));
    }

    @Test
    @DisplayName("发票列表返回真实分页行（字段与前端 InvoiceVO 一致）")
    void invoicesReturnsPage() throws Exception {
        AdminFinanceDtos.InvoiceVO vo = new AdminFinanceDtos.InvoiceVO(
                9L, "INV-9", 3L, 25800L, "ISSUED", "/files/inv-9.pdf", 1_700_000_000_000L);
        when(financeQueryService.invoices(any(PageQuery.class), any()))
                .thenReturn(PageResult.of(List.of(vo), 1L, 1, 10));

        mvc.perform(get("/api/admin/invoices").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].invoiceId").value(9))
                .andExpect(jsonPath("$.data.list[0].invoiceNo").value("INV-9"))
                .andExpect(jsonPath("$.data.list[0].customerId").value(3))
                .andExpect(jsonPath("$.data.list[0].amountCent").value(25800))
                .andExpect(jsonPath("$.data.list[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.data.list[0].pdfUrl").value("/files/inv-9.pdf"))
                .andExpect(jsonPath("$.data.list[0].createdAt").value(1_700_000_000_000L));
    }

    @Test
    @DisplayName("合同列表返回真实分页行，到期状态（dueState）透传到查询服务")
    void contractsReturnsPage() throws Exception {
        AdminFinanceDtos.ContractVO vo = new AdminFinanceDtos.ContractVO(
                5L, "CT-2026-005", 3L, 100000L, "ACTIVE", 1_800_000_000_000L);
        when(financeQueryService.contracts(any(PageQuery.class), isNull(), eq("SOON")))
                .thenReturn(PageResult.of(List.of(vo), 1L, 1, 10));

        mvc.perform(get("/api/admin/contracts")
                        .param("page", "1").param("size", "10").param("dueState", "SOON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].contractId").value(5))
                .andExpect(jsonPath("$.data.list[0].contractNo").value("CT-2026-005"))
                .andExpect(jsonPath("$.data.list[0].amountCent").value(100000))
                .andExpect(jsonPath("$.data.list[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.list[0].validToMillis").value(1_800_000_000_000L));
    }

    @Test
    @DisplayName("新建开票：按订单号创建申请并开具，返回发票号 200")
    void applyInvoiceReturns200() throws Exception {
        when(financeQueryService.applyInvoice(any())).thenReturn(
                new AdminFinanceDtos.InvoiceIssueVO(
                        77L, "INV-77", "SO20261108000001", "ISSUED", "/files/inv-77.pdf"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNo", "SO20261108000001");
        body.put("title", "森特体育用品有限公司");
        body.put("taxNo", "91320000XXXXXXXXXX");
        body.put("email", "finance@example.com");
        body.put("pdfUrl", "/files/inv-77.pdf");
        mvc.perform(post("/api/admin/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.invoiceId").value(77))
                .andExpect(jsonPath("$.data.invoiceNo").value("INV-77"))
                .andExpect(jsonPath("$.data.status").value("ISSUED"));
        verify(financeQueryService).applyInvoice(any());
    }

    @Test
    @DisplayName("新建开票：订单号为空被 Bean Validation 拦截，不得进入业务")
    void applyInvoiceBlankOrderNoRejects() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNo", "");
        body.put("title", "森特体育用品有限公司");
        body.put("pdfUrl", "/files/inv.pdf");
        mvc.perform(post("/api/admin/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("对不存在的申请开票：404 且返回可执行原因，而不是泛泛「资源不存在」")
    void issueInvoiceNotFoundReturnsActionableMessage() throws Exception {
        when(financeQueryService.issueInvoice(eq(1L), any(), any()))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND,
                        "开票申请不存在，请先按订单号新建开票申请"));
        mvc.perform(post("/api/admin/invoices/1/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("pdfUrl", "/files/inv.pdf"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("开票申请不存在，请先按订单号新建开票申请"));
    }

    @Test
    @DisplayName("回款列表返回真实分页，并把客户 / 状态下推到服务层")
    void paymentRecordsReturnsPageAndPassesFilters() throws Exception {
        AdminFinanceDtos.PaymentRecordVO vo = new AdminFinanceDtos.PaymentRecordVO(
                "PR-1", 9002L, 120000L, 20000L, "BANK-1", "PARTIAL",
                1794000000000L, 1794000001000L, "首笔回款", null);
        when(financeQueryService.paymentRecords(any(), eq(9002L), eq("PARTIAL")))
                .thenReturn(PageResult.of(List.of(vo), 1L, 1, 20));

        mvc.perform(get("/api/admin/payment-records")
                        .param("page", "1").param("size", "20")
                        .param("customerId", "9002").param("status", "PARTIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].paymentNo").value("PR-1"))
                .andExpect(jsonPath("$.data.list[0].writtenOffCent").value(20000))
                .andExpect(jsonPath("$.data.list[0].serialNo").value("BANK-1"));

        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(financeQueryService).paymentRecords(captor.capture(), eq(9002L), eq("PARTIAL"));
        assertEquals(1, captor.getValue().getPage());
        assertEquals(20, captor.getValue().getSize());
    }

    @Test
    @DisplayName("回款列表无筛选时仍返回 200 空分页")
    void paymentRecordsWithoutFiltersReturnsEmptyPage() throws Exception {
        when(financeQueryService.paymentRecords(any(), isNull(), isNull()))
                .thenReturn(PageResult.of(List.of(), 0L, 1, 20));

        mvc.perform(get("/api/admin/payment-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
