package com.sent.sports.admin;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.service.CustomBatchDeleteUseCase;
import com.sent.sports.finance.mapper.ContractMapper;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.service.ContractBatchDeleteUseCase;
import com.sent.sports.finance.service.InvoiceBatchDeleteUseCase;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.service.OrderBatchDeleteUseCase;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.service.RefundBatchDeleteUseCase;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.service.StockBatchDeleteUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端六列表批量删除（C2）TDD 红用例。
 *
 * <p>业务口径：只打软删标记，不动任何金额 / 支付 / 财务流水；
 * 列表与 count 同条件过滤已删行；确认令牌的目标串由排序去重后的 ID 生成，
 * 前端换令牌与后端消费必须用同一口径。</p>
 */
@ExtendWith(MockitoExtension.class)
class BatchDeleteUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-17T08:30:00Z"), ZoneOffset.UTC);

    @Mock private RefundOrderMapper refundOrderMapper;
    @Mock private InvoiceMapper invoiceMapper;
    @Mock private ContractMapper contractMapper;
    @Mock private CustomOrderMapper customOrderMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private StockMapper stockMapper;

    // ---------------- BatchIds：令牌目标串口径 ----------------

    @Test
    void batchIds_sortsDedupsAndJoins_stableTargetId() {
        List<String> ids = BatchIds.normalize(List.of("RF2", "RF1", "RF2", " RF3 "));
        assertThat(ids).containsExactly("RF1", "RF2", "RF3");
        assertThat(BatchIds.targetId(List.of("RF3", "RF1", "RF2"))).isEqualTo("RF1,RF2,RF3");
    }

    @Test
    void batchIds_rejectsEmptyBlankAndOversize() {
        assertThatThrownBy(() -> BatchIds.normalize(List.of()))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> BatchIds.normalize(List.of("  ", "")))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> BatchIds.normalize(
                java.util.stream.IntStream.range(0, 201).mapToObj(i -> "ID" + i).toList()))
                .isInstanceOf(BizException.class);
    }

    // ---------------- 退款审核 ----------------

    @Test
    void refundBatchDelete_marksDeletedOnlySelected() {
        when(refundOrderMapper.batchMarkDeleted(List.of("RF1", "RF2"))).thenReturn(2);
        RefundBatchDeleteUseCase uc = new RefundBatchDeleteUseCase(refundOrderMapper);

        assertThat(uc.delete(List.of("RF2", "RF1", "RF2"))).isEqualTo(2);
        verify(refundOrderMapper).batchMarkDeleted(List.of("RF1", "RF2"));
    }

    @Test
    void refundBatchDelete_emptyIds_failsBeforeAnyWrite() {
        RefundBatchDeleteUseCase uc = new RefundBatchDeleteUseCase(refundOrderMapper);
        assertThatThrownBy(() -> uc.delete(List.of()))
                .isInstanceOf(BizException.class);
        verify(refundOrderMapper, never()).batchMarkDeleted(any());
    }

    // ---------------- 发票管理 ----------------

    @Test
    void invoiceBatchDelete_marksDeletedByInvoiceNo() {
        when(invoiceMapper.batchMarkDeleted(List.of("FP-1", "FP-2"))).thenReturn(2);
        InvoiceBatchDeleteUseCase uc = new InvoiceBatchDeleteUseCase(invoiceMapper);

        assertThat(uc.delete(List.of("FP-2", "FP-1"))).isEqualTo(2);
        verify(invoiceMapper).batchMarkDeleted(List.of("FP-1", "FP-2"));
    }

    // ---------------- 合同管理 ----------------

    @Test
    void contractBatchDelete_marksDeletedByContractNo() {
        when(contractMapper.batchMarkDeleted(List.of("HT-1"))).thenReturn(1);
        ContractBatchDeleteUseCase uc = new ContractBatchDeleteUseCase(contractMapper);

        assertThat(uc.delete(List.of("HT-1"))).isEqualTo(1);
        verify(contractMapper).batchMarkDeleted(List.of("HT-1"));
    }

    // ---------------- 定制列表 ----------------

    @Test
    void customBatchDelete_marksDeletedByCustomNo() {
        when(customOrderMapper.batchMarkDeleted(List.of("CU1", "CU2"))).thenReturn(2);
        CustomBatchDeleteUseCase uc = new CustomBatchDeleteUseCase(customOrderMapper);

        assertThat(uc.delete(List.of("CU2", "CU1"))).isEqualTo(2);
        verify(customOrderMapper).batchMarkDeleted(List.of("CU1", "CU2"));
    }

    // ---------------- 订单列表 ----------------

    @Test
    void orderBatchDelete_writesAdminDeletedAt_notCustomerDeletedAt() {
        LocalDateTime at = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
        when(orderMapper.batchMarkAdminDeleted(List.of("SO1"), at)).thenReturn(1);
        OrderBatchDeleteUseCase uc = new OrderBatchDeleteUseCase(orderMapper, CLOCK);

        assertThat(uc.delete(List.of("SO1"))).isEqualTo(1);
        verify(orderMapper).batchMarkAdminDeleted(List.of("SO1"), at);
        // 客户侧删除时间绝不能被管理端删除改写
        verify(orderMapper, never()).markCustomerDeleted(any(), anyLong());
    }

    // ---------------- 库存列表 ----------------

    @Test
    void stockBatchDelete_marksSkuDeletedAndOffShelf() {
        when(stockMapper.batchMarkDeleted(List.of(9101L, 9102L))).thenReturn(2);
        StockBatchDeleteUseCase uc = new StockBatchDeleteUseCase(stockMapper);

        assertThat(uc.delete(List.of("9102", "9101"))).isEqualTo(2);
        verify(stockMapper).batchMarkDeleted(List.of(9101L, 9102L));
    }

    @Test
    void stockBatchDelete_nonNumericId_failsBeforeAnyWrite() {
        StockBatchDeleteUseCase uc = new StockBatchDeleteUseCase(stockMapper);
        assertThatThrownBy(() -> uc.delete(List.of("9101", "ABC")))
                .isInstanceOf(BizException.class);
        verify(stockMapper, never()).batchMarkDeleted(any());
    }

    @Test
    void batchDelete_zeroAffected_isIdempotentSuccess() {
        when(contractMapper.batchMarkDeleted(eq(List.of("HT-GONE")))).thenReturn(0);
        ContractBatchDeleteUseCase uc = new ContractBatchDeleteUseCase(contractMapper);
        // 重复删除 / 已被别人删掉：0 行也算成功，不抛异常
        assertThat(uc.delete(List.of("HT-GONE"))).isZero();
    }
}
