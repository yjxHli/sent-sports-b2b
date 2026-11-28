package com.sent.sports.refund;

import com.sent.sports.common.util.IdGen;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.order.service.dto.RefundLineView;
import com.sent.sports.payment.service.PaymentRefundGateway;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.RefundSuccessNotifier;
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
    @Mock
    private com.sent.sports.member.service.MemberRefundGateway memberRefundGateway;
    @Mock
    private com.sent.sports.coupon.service.CouponDeduction couponDeduction;
    @Mock
    private RefundSuccessNotifier refundSuccessNotifier;

    private IdGen idGen;
    private RefundServiceImpl service;

    @BeforeEach
    void setUp() {
        idGen = new IdGen(); // 无 JdbcTemplate，退化为进程内自增，单测无需数据库
        service = new RefundServiceImpl(refundRepository, idGen,
                Clock.fixed(Instant.parse("2026-11-08T06:00:00Z"), ZoneOffset.UTC),
                refundOrderMapper, paymentRefundGateway, orderRefundViewService, memberRefundGateway,
                couponDeduction, refundSuccessNotifier);
    }

    @Test
    @DisplayName("关单全额退款：先调网关，再落 refund_order（REFUNDED / ADMIN_CLOSE / 原因）")
    void createCloseRefundPersistsRealRow() {
        String refundNo = service.createCloseRefund("SO-1", 11L, 20000L, "客户取消");

        verify(paymentRefundGateway).refund("SO-1", 20000L);
        // 该单没用积分（订单视图取不到 → 退回积分为 0）
        verify(refundOrderMapper).insertCloseRefund(refundNo, 11L, 20000L, "客户取消", 0L);
        ArgumentCaptor<com.sent.sports.refund.domain.RefundOrder> notice =
                ArgumentCaptor.forClass(com.sent.sports.refund.domain.RefundOrder.class);
        verify(refundSuccessNotifier).notify(notice.capture());
        assertEquals(RefundStatus.REFUNDED, notice.getValue().getStatus());
        assertEquals(refundNo, notice.getValue().getRefundNo());
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
                anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("关单全额退款：订单用过积分时一并退回，并落 refund_order.point_refund")
    void createCloseRefundReturnsDeductedPoints() {
        when(orderRefundViewService.getView("SO-3")).thenReturn(Optional.of(new OrderRefundView(
                "SO-3", 7L, 11L, List.of(new RefundLineView(9L, 10000L, 0L)), 0L, false, 7000L)));
        when(memberRefundGateway.deductedPointsOf(7L, "SO-3")).thenReturn(3000L);

        String refundNo = service.createCloseRefund("SO-3", 11L, 7000L, "客户取消");

        // 关单是按实付退的（7000 < 商品额 10000），仍属整单，抵扣的 3000 必须全额退回
        verify(memberRefundGateway).returnDeductedPoints(7L, refundNo, 3000L);
        verify(refundOrderMapper).insertCloseRefund(refundNo, 11L, 7000L, "客户取消", 3000L);
    }

    @Test
    @DisplayName("关单全额退款：下单时核销的优惠券一并退回账户")
    void createCloseRefundReturnsCoupon() {
        when(orderRefundViewService.getView("SO-4"))
                .thenReturn(Optional.of(new OrderRefundView("SO-4", 7L, 11L, List.of(), 0L, false, 5000L)));

        service.createCloseRefund("SO-4", 11L, 5000L, "定制图稿超时");

        verify(couponDeduction).returnCoupon("SO-4");
    }
}
