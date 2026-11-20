package com.sent.sports.admin;

import com.sent.sports.common.error.BizException;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.service.CustomerBatchDeleteUseCase;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.mapper.PaymentRecordMapper;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.WriteOffMapper;
import com.sent.sports.finance.service.EnterpriseAuditBatchDeleteUseCase;
import com.sent.sports.finance.service.PaymentRecordBatchDeleteUseCase;
import com.sent.sports.finance.service.ReceivableBatchDeleteUseCase;
import com.sent.sports.finance.service.WriteOffBatchDeleteUseCase;
import com.sent.sports.stock.mapper.StockFlowMapper;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.service.StockFlowBatchDeleteUseCase;
import com.sent.sports.stock.service.StockWarningBatchDeleteUseCase;
import com.sent.sports.system.mapper.OperationLogMapper;
import com.sent.sports.system.service.OperationLogBatchDeleteUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端 10 类表格批量删除（TDD 红 → 绿）。
 * <p>口径与发票一致：ID 排序去重、只打 deleted 标记、0 行算幂等成功、
 * 非数字主键在写库前失败；金额与业务流水永不改动。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminTableBatchDeleteUseCaseTest {

    @Mock private CustomerMapper customerMapper;
    @Mock private EnterpriseProfileMapper enterpriseProfileMapper;
    @Mock private ReceivableMapper receivableMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private WriteOffMapper writeOffMapper;
    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private StockMapper stockMapper;
    @Mock private OperationLogMapper operationLogMapper;

    @Test
    void customerListBatchDeleteMarksDeletedOnly() {
        when(customerMapper.batchMarkDeleted(List.of(101L, 102L))).thenReturn(2);
        assertThat(new CustomerBatchDeleteUseCase(customerMapper).delete(List.of("102", "101", "102"))).isEqualTo(2);
        verify(customerMapper).batchMarkDeleted(List.of(101L, 102L));
    }

    @Test
    void enterpriseAuditBatchDeleteMarksProfileOnly() {
        when(enterpriseProfileMapper.batchMarkDeleted(List.of(201L))).thenReturn(1);
        assertThat(new EnterpriseAuditBatchDeleteUseCase(enterpriseProfileMapper)
                .delete(List.of("201"))).isEqualTo(1);
        verify(enterpriseProfileMapper).batchMarkDeleted(List.of(201L));
    }

    @Test
    void receivableBatchDeleteNeverTouchesAmounts() {
        when(receivableMapper.batchMarkDeleted(List.of(301L))).thenReturn(1);
        assertThat(new ReceivableBatchDeleteUseCase(receivableMapper).delete(List.of("301"))).isEqualTo(1);
        verify(receivableMapper).batchMarkDeleted(List.of(301L));
        verify(receivableMapper, never()).updateStatus(301L, "DELETED");
    }

    @Test
    void paymentRecordBatchDeleteUsesRecordNo() {
        when(paymentRecordMapper.batchMarkDeleted(List.of("SK-1", "SK-2"))).thenReturn(2);
        assertThat(new PaymentRecordBatchDeleteUseCase(paymentRecordMapper)
                .delete(List.of("SK-2", "SK-1"))).isEqualTo(2);
        verify(paymentRecordMapper).batchMarkDeleted(List.of("SK-1", "SK-2"));
    }

    @Test
    void writeOffBatchDeleteMarksDetailsOnly() {
        when(writeOffMapper.batchMarkDeleted(List.of(401L))).thenReturn(1);
        assertThat(new WriteOffBatchDeleteUseCase(writeOffMapper).delete(List.of("401"))).isEqualTo(1);
        verify(writeOffMapper).batchMarkDeleted(List.of(401L));
    }

    @Test
    void stockFlowBatchDeleteKeepsQuantities() {
        when(stockFlowMapper.batchMarkDeleted(List.of(501L))).thenReturn(1);
        assertThat(new StockFlowBatchDeleteUseCase(stockFlowMapper).delete(List.of("501"))).isEqualTo(1);
        verify(stockFlowMapper).batchMarkDeleted(List.of(501L));
    }

    @Test
    void stockWarningBatchDeleteMarksLedgerOnly() {
        when(stockMapper.batchMarkWarningDeleted(List.of(601L))).thenReturn(1);
        assertThat(new StockWarningBatchDeleteUseCase(stockMapper).delete(List.of("601"))).isEqualTo(1);
        verify(stockMapper).batchMarkWarningDeleted(List.of(601L));
    }

    @Test
    void operationLogBatchDeleteMarksLogsOnly() {
        when(operationLogMapper.batchMarkDeleted(List.of(701L))).thenReturn(1);
        assertThat(new OperationLogBatchDeleteUseCase(operationLogMapper).delete(List.of("701"))).isEqualTo(1);
        verify(operationLogMapper).batchMarkDeleted(List.of(701L));
    }

    @Test
    void nonNumericIdFailsBeforeAnyWrite() {
        assertThatThrownBy(() -> new CustomerBatchDeleteUseCase(customerMapper).delete(List.of("101", "ABC")))
                .isInstanceOf(BizException.class);
        verify(customerMapper, never()).batchMarkDeleted(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void zeroAffectedIsIdempotentSuccess() {
        when(writeOffMapper.batchMarkDeleted(List.of(999L))).thenReturn(0);
        assertThat(new WriteOffBatchDeleteUseCase(writeOffMapper).delete(List.of("999"))).isZero();
    }
}
