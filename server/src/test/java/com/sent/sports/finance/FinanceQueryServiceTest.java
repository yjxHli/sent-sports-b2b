package com.sent.sports.finance;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.finance.dto.admin.AdminFinanceDtos;
import com.sent.sports.finance.mapper.ContractMapper;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.PaymentRecordMapper;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.PaymentRecordPO;
import com.sent.sports.finance.mapper.po.ContractListItemPO;
import com.sent.sports.finance.mapper.po.ReceivableListRow;
import com.sent.sports.finance.service.impl.FinanceQueryServiceImpl;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 财务查询实现测试：应收台账分页与逾期天数口径（原 P4 桩恒返回空，导致确认收货后台账看不到数据）。
 */
class FinanceQueryServiceTest extends BaseUseCaseTest {

    @Mock
    private InvoiceMapper invoiceMapper;
    @Mock
    private ContractMapper contractMapper;
    @Mock
    private ReceivableMapper receivableMapper;
    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @InjectMocks
    private FinanceQueryServiceImpl queryService;

    private static PageQuery pageQuery() {
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setSize(10);
        return query;
    }

    private static ReceivableListRow row(long id, String orderNo, long amountCent, long writtenOffCent,
                                         LocalDate dueAt, String status) {
        ReceivableListRow row = new ReceivableListRow();
        row.setReceivableId(id);
        row.setOrderNo(orderNo);
        row.setCustomerId(9002L);
        row.setAmountCent(amountCent);
        row.setWrittenOffCent(writtenOffCent);
        row.setDueAt(dueAt);
        row.setStatus(status);
        return row;
    }

    @Test
    void receivablesMapsRowsAndComputesOverdueDays() {
        LocalDate duePast = LocalDate.now().minusDays(5);
        when(receivableMapper.selectReceivablePage(any(Page.class), anyLong(), isNull(), eq(false)))
                .thenAnswer(inv -> {
                    Page<ReceivableListRow> page = inv.getArgument(0);
                    page.setRecords(List.of(
                            row(444L, "SO20261107001000", 20000L, 0L, duePast, "NORMAL"),
                            row(445L, "SO20261107001001", 10000L, 10000L, LocalDate.now().plusDays(10), "CLOSED")));
                    return page;
                });
        when(receivableMapper.countReceivable(anyLong(), isNull(), eq(false))).thenReturn(2L);

        PageResult<AdminFinanceDtos.ReceivableVO> result =
                queryService.receivables(pageQuery(), 9002L, null, false);

        assertEquals(2L, result.getTotal());
        AdminFinanceDtos.ReceivableVO first = result.getList().get(0);
        assertEquals("SO20261107001000", first.orderNo());
        assertEquals(0L, first.writtenOffCent());
        assertEquals(5, first.overdueDays(), "到期日已过 5 天且未结清 → 逾期 5 天");
        assertTrue(first.dueAt() > 0L);

        AdminFinanceDtos.ReceivableVO closed = result.getList().get(1);
        assertEquals("CLOSED", closed.status());
        assertEquals(0, closed.overdueDays(), "已结清不计逾期");
    }

    @Test
    void receivablesEmptyReturnsZeroTotal() {
        when(receivableMapper.selectReceivablePage(any(Page.class), isNull(), isNull(), eq(false)))
                .thenAnswer(inv -> {
                    Page<ReceivableListRow> page = inv.getArgument(0);
                    page.setRecords(List.of());
                    return page;
                });
        when(receivableMapper.countReceivable(isNull(), isNull(), eq(false))).thenReturn(0L);

        PageResult<AdminFinanceDtos.ReceivableVO> result =
                queryService.receivables(pageQuery(), null, null, false);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    @DisplayName("应收台账：openOnly 下推 SQL（只查未结清），列表与 count 用同一条件，避免翻页时尾巴混入已结清")
    void receivablesOpenOnlyPushesDownToSql() {
        when(receivableMapper.selectReceivablePage(any(Page.class), eq(9002L), isNull(), eq(true)))
                .thenAnswer(inv -> {
                    Page<ReceivableListRow> p = inv.getArgument(0);
                    p.setRecords(List.of(
                            row(446L, "SO20261107001002", 30000L, 0L, LocalDate.now(), "NORMAL")));
                    return p;
                });
        when(receivableMapper.countReceivable(eq(9002L), isNull(), eq(true))).thenReturn(1L);

        PageResult<AdminFinanceDtos.ReceivableVO> result =
                queryService.receivables(pageQuery(), 9002L, null, true);

        verify(receivableMapper).selectReceivablePage(any(Page.class), eq(9002L), isNull(), eq(true));
        verify(receivableMapper).countReceivable(eq(9002L), isNull(), eq(true));
        assertEquals(1L, result.getTotal());
        assertEquals("SO20261107001002", result.getList().get(0).orderNo());
    }

    @Test
    void applyInvoiceCreatesRequestAndMarksIssued() {
        AdminFinanceDtos.InvoiceApplyCmd cmd = new AdminFinanceDtos.InvoiceApplyCmd(
                "SO-APPLY-1", "森特体育用品有限公司", "91320000XX", "f@example.com",
                "/files/inv.pdf", "备注");
        when(invoiceMapper.insertFromOrder(eq("SO-APPLY-1"), eq("森特体育用品有限公司"),
                eq("91320000XX"), eq("f@example.com"))).thenReturn(1);
        when(invoiceMapper.selectLatestIdByOrderNo("SO-APPLY-1")).thenReturn(77L);
        when(invoiceMapper.markIssued(77L, "INV-77", "/files/inv.pdf")).thenReturn(1);

        AdminFinanceDtos.InvoiceIssueVO vo = queryService.applyInvoice(cmd);

        assertEquals(77L, vo.invoiceId());
        assertEquals("INV-77", vo.invoiceNo());
        assertEquals("SO-APPLY-1", vo.orderNo());
        assertEquals("ISSUED", vo.status());
        verify(invoiceMapper).markIssued(77L, "INV-77", "/files/inv.pdf");
    }

    @Test
    @DisplayName("开票未填 PDF 地址：由系统按发票 ID 生成下载端点，不再强制财务手填不存在的文件地址")
    void applyInvoiceDerivesPdfUrlWhenBlank() {
        AdminFinanceDtos.InvoiceApplyCmd cmd = new AdminFinanceDtos.InvoiceApplyCmd(
                "SO-APPLY-2", "森特体育用品有限公司", null, null, "  ", null);
        when(invoiceMapper.insertFromOrder(eq("SO-APPLY-2"), eq("森特体育用品有限公司"),
                isNull(), isNull())).thenReturn(1);
        when(invoiceMapper.selectLatestIdByOrderNo("SO-APPLY-2")).thenReturn(88L);
        when(invoiceMapper.markIssued(88L, "INV-88", "/api/admin/invoices/88/pdf")).thenReturn(1);

        AdminFinanceDtos.InvoiceIssueVO vo = queryService.applyInvoice(cmd);

        assertEquals("/api/admin/invoices/88/pdf", vo.pdfUrl());
        verify(invoiceMapper).markIssued(88L, "INV-88", "/api/admin/invoices/88/pdf");
    }

    @Test
    @DisplayName("换开未填 PDF 地址：同样回落到系统生成的下载端点")
    void issueInvoiceDerivesPdfUrlWhenBlank() {
        when(invoiceMapper.markIssued(4L, "INV-4", "/api/admin/invoices/4/pdf")).thenReturn(1);

        Object snapshot = queryService.issueInvoice(4L, null, null);

        assertEquals("/api/admin/invoices/4/pdf",
                ((java.util.Map<?, ?>) snapshot).get("pdfUrl"));
    }

    @Test
    void applyInvoiceRejectsMissingOrder() {        AdminFinanceDtos.InvoiceApplyCmd cmd = new AdminFinanceDtos.InvoiceApplyCmd(
                "NO-SUCH", "抬头", null, null, "/files/inv.pdf", null);
        when(invoiceMapper.insertFromOrder(anyString(), anyString(), any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> queryService.applyInvoice(cmd));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("订单不存在"));
        verify(invoiceMapper, org.mockito.Mockito.never())
                .markIssued(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("合同列表：到期状态（EXPIRED/SOON/NORMAL）下推 SQL，不再由前端截断后过滤")
    void contractsPushesDueStateDown() {
        Page<ContractListItemPO> page = new Page<>(1, 10);
        when(contractMapper.selectContractPage(
                ArgumentMatchers.<Page<ContractListItemPO>>any(), isNull(), eq("SOON")))
                .thenReturn(page);
        when(contractMapper.countContract(isNull(), eq("SOON"))).thenReturn(0L);

        PageResult<AdminFinanceDtos.ContractVO> result =
                queryService.contracts(pageQuery(), "  ", " soon ");

        verify(contractMapper).selectContractPage(
                ArgumentMatchers.<Page<ContractListItemPO>>any(), isNull(), eq("SOON"));
        verify(contractMapper).countContract(isNull(), eq("SOON"));
        assertEquals(0, result.getList().size());
    }

    @Test
    @DisplayName("合同列表：非法到期状态归一为 null，返回全部而不是空列表")
    void contractsNormalizesUnknownDueState() {
        Page<ContractListItemPO> page = new Page<>(1, 10);
        when(contractMapper.selectContractPage(
                ArgumentMatchers.<Page<ContractListItemPO>>any(), isNull(), isNull()))
                .thenReturn(page);
        when(contractMapper.countContract(isNull(), isNull())).thenReturn(53L);

        PageResult<AdminFinanceDtos.ContractVO> result =
                queryService.contracts(pageQuery(), null, "WHATEVER");

        verify(contractMapper).countContract(isNull(), isNull());
        assertEquals(53L, result.getTotal());
    }

    @Test
    void issueInvoiceNotFoundGivesActionableMessage() {
        when(invoiceMapper.markIssued(999L, "INV-999", "/files/inv.pdf")).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> queryService.issueInvoice(999L, "/files/inv.pdf", null));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("开票申请不存在"));
    }

    @Test
    void paymentRecordsMapsRowsAndKeepsIndependentTotal() {
        PaymentRecordPO po = new PaymentRecordPO();
        po.setRecordNo("PR-1");
        po.setCustomerId(9002L);
        po.setAmountCent(120000L);
        po.setWrittenOffCent(20000L);
        po.setBankSerial("BANK-1");
        po.setStatus("PARTIAL");
        po.setRemark("首笔回款");
        po.setReversedReason(null);
        po.setReceivedDate(LocalDate.of(2026, 11, 8));
        po.setCreatedAt(LocalDateTime.of(2026, 11, 8, 10, 0));
        when(paymentRecordMapper.selectPaymentRecordPage(any(Page.class), eq(9002L), eq("PARTIAL")))
                .thenAnswer(inv -> {
                    Page<PaymentRecordPO> page = inv.getArgument(0);
                    page.setRecords(List.of(po));
                    return page;
                });
        when(paymentRecordMapper.countPaymentRecord(eq(9002L), eq("PARTIAL"))).thenReturn(1L);

        PageResult<AdminFinanceDtos.PaymentRecordVO> result =
                queryService.paymentRecords(pageQuery(), 9002L, "PARTIAL");

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        AdminFinanceDtos.PaymentRecordVO vo = result.getList().get(0);
        assertEquals("PR-1", vo.paymentNo());
        assertEquals(9002L, vo.customerId());
        assertEquals(120000L, vo.amountCent());
        assertEquals(20000L, vo.writtenOffCent());
        assertEquals("BANK-1", vo.serialNo());
        assertEquals("PARTIAL", vo.status());
        assertEquals("首笔回款", vo.remark());
        assertEquals(po.getReceivedDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                vo.receivedDateMillis());
        assertEquals(po.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                vo.createdAtMillis());
    }

    @Test
    void paymentRecordsEmptyReturnsZeroTotal() {
        when(paymentRecordMapper.selectPaymentRecordPage(any(Page.class), isNull(), isNull()))
                .thenAnswer(inv -> {
                    Page<PaymentRecordPO> page = inv.getArgument(0);
                    page.setRecords(List.of());
                    return page;
                });
        when(paymentRecordMapper.countPaymentRecord(isNull(), isNull())).thenReturn(0L);

        PageResult<AdminFinanceDtos.PaymentRecordVO> result =
                queryService.paymentRecords(pageQuery(), null, null);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }
}
