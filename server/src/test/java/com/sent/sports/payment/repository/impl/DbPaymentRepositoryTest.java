package com.sent.sports.payment.repository.impl;

import com.sent.sports.payment.domain.PaymentTxn;
import com.sent.sports.payment.mapper.PaymentTxnMapper;
import com.sent.sports.payment.mapper.po.PaymentTxnPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关单退款回归：已支付流水转 REFUNDED 必须走专用条件更新，
 * 不能复用 closeOnFailure（那条 SQL 只匹配 CREATED，会让支付流水停在 PAID）。
 */
@ExtendWith(MockitoExtension.class)
class DbPaymentRepositoryTest {

    @Mock
    private PaymentTxnMapper mapper;

    @Test
    @DisplayName("REFUNDED 走 markRefunded(PAID→REFUNDED)，不走 closeOnFailure")
    void refundedUsesDedicatedUpdate() {
        PaymentTxn txn = new PaymentTxn("TXN001", "SO-1", 20000L, "MOCK", "idem-1", 0L);
        txn.markRefunded();
        when(mapper.selectByTxnNo("TXN001")).thenReturn(new PaymentTxnPO());
        DbPaymentRepository repository = new DbPaymentRepository(mapper);

        repository.save(txn);

        verify(mapper).markRefunded("TXN001");
        verify(mapper, org.mockito.Mockito.never()).closeOnFailure(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
