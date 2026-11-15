package com.sent.sports.finance;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.dto.UserInvoiceDtos;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.po.InvoiceApplyRow;
import com.sent.sports.finance.mapper.po.MyOrderStatePO;
import com.sent.sports.finance.service.impl.UserInvoiceServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户端「我的订单 → 申请开票」用例（TDD 红→绿）。
 *
 * <p><b>2026-11-15 口径变更（用户明确要求）</b>：只有「已完成」的订单才能申请开票。
 * 原口径是「付过款就能开（PAID / PART_REFUNDED）」，导致在途订单（待发货 / 待收货）
 * 也能开票——货还没到手、可能退换，票面金额与实际成交不一致，财务无法核销。</p>
 *
 * <p>因此这里锁住四条关键口径：</p>
 * <ol>
 *   <li>订单主状态必须是 {@code COMPLETED}，在途 / 未付款 / 已取消一律 40901；</li>
 *   <li>同时支付状态必须仍在 {@code PAID / PART_REFUNDED}（已完成但整单全退的不该再开票）；</li>
 *   <li>重复提交按幂等返回已有申请，连点两下不能开出两张票；</li>
 *   <li>归属只认 {@code customerId}，他人订单统一 404，不能用订单号探测订单是否存在。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class UserInvoiceServiceImplTest {

    private static final long CUSTOMER = 9002L;
    private static final String ORDER_NO = "SO20261115000001";

    @Mock
    private InvoiceMapper invoiceMapper;

    @InjectMocks
    private UserInvoiceServiceImpl service;

    private static UserInvoiceDtos.ApplyCmd cmd(String type, String taxNo) {
        return new UserInvoiceDtos.ApplyCmd(ORDER_NO, "森特体育用品有限公司", taxNo, type, "fin@sent.com");
    }

    /** 订单主状态 + 支付状态（一次查询同时带出，前端「查看发票」也要用它判归属） */
    private static MyOrderStatePO state(String status, String payStatus) {
        MyOrderStatePO po = new MyOrderStatePO();
        po.setStatus(status);
        po.setPayStatus(payStatus);
        return po;
    }

    private static InvoiceApplyRow row(String status, String invoiceNo) {
        InvoiceApplyRow row = new InvoiceApplyRow();
        row.setInvoiceId(31L);
        row.setOrderNo(ORDER_NO);
        row.setStatus(status);
        row.setTitle("森特体育用品有限公司");
        row.setType("NORMAL");
        row.setEmail("fin@sent.com");
        row.setInvoiceNo(invoiceNo);
        row.setAmountCent(128_800L);
        row.setCreatedAt(LocalDateTime.of(2026, 11, 15, 10, 30, 0));
        return row;
    }

    @Test
    @DisplayName("已完成订单可申请开票：落库后回读，票面金额取订单应付")
    void applyOnCompletedOrder() {
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER))
                .thenReturn(state("COMPLETED", "PAID"));
        when(invoiceMapper.insertUserApplication(eq(ORDER_NO), eq(CUSTOMER), anyString(),
                any(), anyString(), anyString())).thenReturn(1);
        when(invoiceMapper.selectMyLatestByOrderNo(ORDER_NO, CUSTOMER))
                .thenReturn(row("APPLIED", null));

        UserInvoiceDtos.InvoiceVO vo = service.apply(CUSTOMER, cmd(null, null));

        assertEquals("APPLIED", vo.status());
        assertEquals(128_800L, vo.amountCent());
        assertEquals("1288.00", vo.amountYuan());
        assertEquals("31", vo.invoiceId());
        // type 缺省按普通发票落库，不能写 null 让 DB 默认值兜（DB 默认值只对 INSERT 省略列生效）
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(invoiceMapper).insertUserApplication(eq(ORDER_NO), eq(CUSTOMER), anyString(),
                any(), type.capture(), anyString());
        assertEquals("NORMAL", type.getValue());
    }

    @Test
    @DisplayName("已付款但未完成（待发货）拒绝开票，且不写库")
    void rejectShippedButNotCompletedOrder() {
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER))
                .thenReturn(state("WAIT_SHIP", "PAID"));

        BizException ex = assertThrows(BizException.class, () -> service.apply(CUSTOMER, cmd(null, null)));

        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        assertEquals("订单完成后才能申请开票", ex.resolvedMessage());
        verify(invoiceMapper, never()).insertUserApplication(anyString(), anyLong(),
                anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("待收货同样不算完成，拒绝开票")
    void rejectWaitReceiveOrder() {
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER))
                .thenReturn(state("WAIT_RECEIVE", "PAID"));

        BizException ex = assertThrows(BizException.class, () -> service.apply(CUSTOMER, cmd(null, null)));

        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("未支付订单拒绝开票，且不写库")
    void rejectUnpaidOrder() {
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER))
                .thenReturn(state("WAIT_PAY", "UNPAID"));

        BizException ex = assertThrows(BizException.class, () -> service.apply(CUSTOMER, cmd(null, null)));

        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(invoiceMapper, never()).insertUserApplication(anyString(), anyLong(),
                anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("已完成但整单全退不再开票（钱已退，票面无依据）")
    void rejectCompletedButFullyRefundedOrder() {
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER))
                .thenReturn(state("COMPLETED", "FULL_REFUNDED"));

        BizException ex = assertThrows(BizException.class, () -> service.apply(CUSTOMER, cmd(null, null)));

        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(invoiceMapper, never()).insertUserApplication(anyString(), anyLong(),
                anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("已完成且部分退款仍可开票（有实付金额）")
    void allowCompletedPartRefundedOrder() {
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER))
                .thenReturn(state("COMPLETED", "PART_REFUNDED"));
        when(invoiceMapper.insertUserApplication(eq(ORDER_NO), eq(CUSTOMER), anyString(),
                any(), anyString(), anyString())).thenReturn(1);
        when(invoiceMapper.selectMyLatestByOrderNo(ORDER_NO, CUSTOMER))
                .thenReturn(row("APPLIED", null));

        assertEquals("APPLIED", service.apply(CUSTOMER, cmd("NORMAL", null)).status());
    }

    @Test
    @DisplayName("重复提交幂等：插入 0 行时返回已有申请，不报错也不新增")
    void duplicateApplyIsIdempotent() {
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER))
                .thenReturn(state("COMPLETED", "PAID"));
        when(invoiceMapper.insertUserApplication(eq(ORDER_NO), eq(CUSTOMER), anyString(),
                any(), anyString(), anyString())).thenReturn(0);
        when(invoiceMapper.selectMyLatestByOrderNo(ORDER_NO, CUSTOMER))
                .thenReturn(row("ISSUED", "INV20261115000001"));

        UserInvoiceDtos.InvoiceVO vo = service.apply(CUSTOMER, cmd(null, null));

        assertEquals("ISSUED", vo.status());
        assertEquals("INV20261115000001", vo.invoiceNo());
    }

    @Test
    @DisplayName("他人订单按 404 处理，不泄露订单是否存在")
    void rejectForeignOrder() {
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.apply(CUSTOMER, cmd(null, null)));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("专用发票必须带税号")
    void specialInvoiceRequiresTaxNo() {
        BizException ex = assertThrows(BizException.class,
                () -> service.apply(CUSTOMER, cmd("SPECIAL", "  ")));

        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        // 参数校验应在查库之前拦住，不必浪费一次 SQL
        verify(invoiceMapper, never()).selectOrderStateOfMyOrder(anyString(), anyLong());
    }

    @Test
    @DisplayName("非法发票类型拒绝，不落脏值")
    void rejectUnknownType() {
        BizException ex = assertThrows(BizException.class,
                () -> service.apply(CUSTOMER, cmd("E_INVOICE", "91320000MA1XXXXX0A")));

        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("查开票状态：从未申请返回 null，供前端继续显示「申请开票」")
    void latestReturnsNullWhenNeverApplied() {
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER))
                .thenReturn(state("COMPLETED", "PAID"));
        when(invoiceMapper.selectMyLatestByOrderNo(ORDER_NO, CUSTOMER)).thenReturn(null);

        assertNull(service.latest(CUSTOMER, ORDER_NO));
    }

    @Test
    @DisplayName("查开票状态：已开具时带出票号与 PDF 地址")
    void latestReturnsIssuedInvoice() {
        InvoiceApplyRow row = row("ISSUED", "INV20261115000001");
        row.setPdfUrl("/api/admin/finance/invoices/31/pdf");
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER))
                .thenReturn(state("COMPLETED", "PAID"));
        when(invoiceMapper.selectMyLatestByOrderNo(ORDER_NO, CUSTOMER)).thenReturn(row);

        UserInvoiceDtos.InvoiceVO vo = service.latest(CUSTOMER, ORDER_NO);

        assertEquals("INV20261115000001", vo.invoiceNo());
        assertEquals("/api/admin/finance/invoices/31/pdf", vo.pdfUrl());
    }

    @Test
    @DisplayName("查开票状态同样校验归属：他人订单 404")
    void latestRejectsForeignOrder() {
        when(invoiceMapper.selectOrderStateOfMyOrder(ORDER_NO, CUSTOMER)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.latest(CUSTOMER, ORDER_NO));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }
}
