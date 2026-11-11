package com.sent.sports.refund;

import com.sent.sports.common.util.IdGen;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.payment.service.PaymentRefundGateway;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.impl.RefundServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 退款门面：关单场景必须真库落 refund_order（REFUNDED），并先经模拟退款网关。 */
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private RefundOrderMapper refundOrderMapper;
    @Mock
    private PaymentRefundGateway paymentRefundGateway;
    @Mock
    private OrderRefundViewService orderRefundViewService;

    private IdGen idGen;
    private RefundServiceImpl service;

    @BeforeEach
    void setUp() {
        idGen = new IdGen(); // 无 JdbcTemplate，退化为进程内自增，单测无需数据库
        service = new RefundServiceImpl(refundRepository, idGen,
                Clock.fixed(Instant.parse("2026-11-08T06:00:00Z"), ZoneOffset.UTC),
                refundOrderMapper, paymentRefundGateway, orderRefundViewService);
    }

    @Test
    @DisplayName("关单全额退款：先调网关，再落 refund_order（REFUNDED / ADMIN_CLOSE / 原因）")
    void createCloseRefundPersistsRealRow() {
        String refundNo = service.createCloseRefund("SO-1", 11L, 20000L, "客户取消");

        verify(paymentRefundGateway).refund("SO-1", 20000L);
        verify(refundOrderMapper).insertCloseRefund(refundNo, 11L, 20000L, "客户取消");
        org.junit.jupiter.api.Assertions.assertTrue(refundNo.startsWith("RF"));
        verify(refundRepository, never()).save(any());
    }

    @Test
    @DisplayName("迟到支付回调的 ONLY_REFUND 经订单视图取 orderId 后写退款仓储（REFUNDING）")
    void createOnlyRefundUnchanged() {
        when(orderRefundViewService.getView("SO-2"))
                .thenReturn(Optional.of(new OrderRefundView("SO-2", 1L, 11L, List.of(), 0L, false)));

        service.createOnlyRefund("SO-2", 9900L, "PAID_AFTER_CANCEL");

        ArgumentCaptor<com.sent.sports.refund.domain.RefundOrder> captor =
                ArgumentCaptor.forClass(com.sent.sports.refund.domain.RefundOrder.class);
        verify(refundRepository).save(captor.capture());
        assertEquals(RefundStatus.REFUNDING, captor.getValue().getStatus());
        assertEquals(11L, captor.getValue().getOrderId());
        verify(refundOrderMapper, never()).insertCloseRefund(any(), anyLong(),
                anyLong(), any());
    }
}
