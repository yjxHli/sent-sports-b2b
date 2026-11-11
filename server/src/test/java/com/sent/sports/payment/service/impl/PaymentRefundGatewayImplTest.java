package com.sent.sports.payment.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.payment.domain.PaymentTxn;
import com.sent.sports.payment.domain.PaymentTxnStatus;
import com.sent.sports.payment.gateway.PayGateway;
import com.sent.sports.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 支付退款门面：模拟网关受理 + 支付流水置 REFUNDED；无流水（余额/授信支付）不阻塞关单。 */
@ExtendWith(MockitoExtension.class)
class PaymentRefundGatewayImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PayGateway payGateway;

    @Test
    @DisplayName("有支付流水：调网关并把流水落为 REFUNDED")
    void refundMarksTxnRefunded() {
        PaymentTxn txn = new PaymentTxn("TXN001", "SO-1", 20000L, "MOCK", "idem-1", 0L);
        when(paymentRepository.findByOrderNo("SO-1")).thenReturn(Optional.of(txn));
        when(payGateway.refund("TXN001", "SO-1", 20000L)).thenReturn(true);
        PaymentRefundGatewayImpl gateway = new PaymentRefundGatewayImpl(paymentRepository, payGateway);

        gateway.refund("SO-1", 20000L);

        verify(payGateway).refund("TXN001", "SO-1", 20000L);
        ArgumentCaptor<PaymentTxn> captor = ArgumentCaptor.forClass(PaymentTxn.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentTxnStatus.REFUNDED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("无支付流水（余额/授信支付）：仍走模拟网关，不写流水也不报错")
    void refundWithoutTxnStillCallsGateway() {
        when(paymentRepository.findByOrderNo("SO-1")).thenReturn(Optional.empty());
        when(payGateway.refund(anyString(), anyString(), anyLong())).thenReturn(true);
        PaymentRefundGatewayImpl gateway = new PaymentRefundGatewayImpl(paymentRepository, payGateway);

        gateway.refund("SO-1", 20000L);

        verify(payGateway).refund("RF-SO-1", "SO-1", 20000L);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("网关拒绝退款：抛 BIZ_REJECT，整个关单事务回滚")
    void gatewayRejectionThrows() {
        PaymentTxn txn = new PaymentTxn("TXN001", "SO-1", 20000L, "MOCK", "idem-1", 0L);
        when(paymentRepository.findByOrderNo("SO-1")).thenReturn(Optional.of(txn));
        when(payGateway.refund(anyString(), anyString(), anyLong())).thenReturn(false);
        PaymentRefundGatewayImpl gateway = new PaymentRefundGatewayImpl(paymentRepository, payGateway);

        assertEquals(ErrorCode.BIZ_REJECT,
                assertThrows(BizException.class, () -> gateway.refund("SO-1", 20000L)).getErrorCode());
        verify(paymentRepository, never()).save(any());
    }
}
