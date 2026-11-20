package com.sent.sports.integration;

import com.sent.sports.member.service.MemberRefundGateway;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.payment.service.CreatePaymentUseCase;
import com.sent.sports.payment.service.PayCallbackUseCase;
import com.sent.sports.payment.service.dto.CreatePaymentCmd;
import com.sent.sports.payment.service.dto.CreatePaymentResult;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.service.RefundCallbackService;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退款退回抵扣积分的验收（主流电商口径）。
 *
 * <p><b>缺陷背景</b>：支付成功会把抵扣积分从 {@code customer.points_balance} 扣掉，
 * 但退款回调只做「按退款金额扣回赠送积分」，<b>从不退还用户花掉的抵扣积分</b>——
 * 用户退了一整单，钱回来了、积分却被吃了。</p>
 *
 * <p><b>验收口径</b>：
 * <ol>
 *   <li>整单退：抵扣了多少退多少，{@code point_log} 一条 {@code type=REFUND_RETURN} 的正向流水并关联退款单号，
 *       {@code refund_order.point_refund} 同步落库；</li>
 *   <li>部分退：按退款额占商品金额的比例退（向下取整，绝不超发）；</li>
 *   <li>同一退款单重放：唯一键兜底，只退一次。</li>
 * </ol>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class RefundReturnPointsIT extends FullStackIntegrationTest {

    private static final long CUSTOMER_ID = 8841L;
    private static final long SKU_ID = 9321L;
    private static final long GOODS_CENT = 10_000L;
    /** 积分抵扣上限 = 商品额 30% = 3000，实付 7000。 */
    private static final long DEDUCT_CENT = 3_000L;
    private static final long PAYABLE_CENT = GOODS_CENT - DEDUCT_CENT;
    private static final long INITIAL_POINTS = 50_000L;
    /** 退款回调还会「按 1 元 = 1 积分」扣回赠送积分（既有口径），断言余额时要把这部分算进去。 */
    private static final long REVOKE_FULL = GOODS_CENT / 100;
    private static final long REVOKE_PARTIAL = 5_000L / 100;

    @Autowired
    private CreateOrderUseCase createOrderUseCase;
    @Autowired
    private CreatePaymentUseCase createPaymentUseCase;
    @Autowired
    private PayCallbackUseCase payCallbackUseCase;
    @Autowired
    private RefundCallbackService refundCallbackService;
    @Autowired
    private MemberRefundGateway memberRefundGateway;

    @Test
    @DisplayName("整单退款：抵扣的 3000 积分原额退回，落 REFUND_RETURN 流水与 refund_order.point_refund")
    void fullRefundReturnsDeductedPoints() {
        String orderNo = seedPaidOrderWithPoints();
        assertThat(pointsBalanceOf()).as("支付成功已扣 3000").isEqualTo(INITIAL_POINTS - DEDUCT_CENT);

        processRefund("RF-RETURN-1", orderNo, GOODS_CENT);

        // 50000 − 3000(抵扣) − 100(扣回赠送) + 3000(退回抵扣) = 49900
        assertThat(pointsBalanceOf()).as("整单退款必须把抵扣积分退回来")
                .isEqualTo(INITIAL_POINTS - DEDUCT_CENT - REVOKE_FULL + DEDUCT_CENT);
        assertThat(jdbc.queryForObject("SELECT type FROM point_log WHERE type = 'REFUND_RETURN' AND source_id = ?",
                String.class, "RF-RETURN-1")).isEqualTo("REFUND_RETURN");
        assertThat(jdbc.queryForObject("SELECT change_value FROM point_log WHERE type = 'REFUND_RETURN' AND source_id = ?",
                Long.class, "RF-RETURN-1")).isEqualTo(DEDUCT_CENT);
        assertThat(jdbc.queryForObject("SELECT balance_after FROM point_log WHERE type = 'REFUND_RETURN' AND source_id = ?",
                Long.class, "RF-RETURN-1"))
                .as("流水快照 = 扣回赠送之后、退回抵扣之后的余额")
                .isEqualTo(INITIAL_POINTS - DEDUCT_CENT - REVOKE_FULL + DEDUCT_CENT);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM point_log WHERE type = 'REFUND_RETURN' AND source_id = ?",
                Long.class, "RF-RETURN-1")).isEqualTo(CUSTOMER_ID);
        assertThat(jdbc.queryForObject("SELECT point_refund FROM refund_order WHERE refund_no = ?",
                Long.class, "RF-RETURN-1")).as("退款单必须落退回积分数").isEqualTo(DEDUCT_CENT);
        assertThat(jdbc.queryForObject("SELECT status FROM refund_order WHERE refund_no = ?",
                String.class, "RF-RETURN-1")).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("部分退款：按退款额占商品额比例退回（5000/10000 → 1500）")
    void partialRefundReturnsProportionally() {
        String orderNo = seedPaidOrderWithPoints();

        processRefund("RF-RETURN-2", orderNo, 5_000L);

        long expected = INITIAL_POINTS - DEDUCT_CENT - REVOKE_PARTIAL + 1_500L;
        assertThat(pointsBalanceOf()).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT change_value FROM point_log WHERE type = 'REFUND_RETURN' AND source_id = ?",
                Long.class, "RF-RETURN-2")).isEqualTo(1_500L);
        assertThat(jdbc.queryForObject("SELECT point_refund FROM refund_order WHERE refund_no = ?",
                Long.class, "RF-RETURN-2")).isEqualTo(1_500L);
    }

    @Test
    @DisplayName("同一退款单重复退回（渠道重试 / 人工重放）：uk_point_source 兜底，只加一次")
    void replayDoesNotReturnTwice() {
        seedPaidOrderWithPoints();

        for (int i = 0; i < 2; i++) {
            memberRefundGateway.returnDeductedPoints(CUSTOMER_ID, "RF-RETURN-3", DEDUCT_CENT);
        }

        assertThat(countOf("point_log")).as("USE + 一条 REFUND_RETURN").isEqualTo(2);
        assertThat(pointsBalanceOf()).as("退回只生效一次").isEqualTo(INITIAL_POINTS);
    }

    // ---------------------------------------------------------------- helpers

    /** 下单一笔「用满 30% 抵扣」并支付成功的真实订单。 */
    private String seedPaidOrderWithPoints() {
        seedCustomer();
        seedSku(SKU_ID, GOODS_CENT, 10);

        CreateOrderResult order = createOrderUseCase.create(new CreateOrderCmd(
                CUSTOMER_ID, seedAddress(CUSTOMER_ID), "IDEM-RETURN-POINTS-1", (int) INITIAL_POINTS,
                Integer.MAX_VALUE, 0, List.of(line(SKU_ID, 1))));
        CreatePaymentResult payment = createPaymentUseCase.create(
                new CreatePaymentCmd(order.orderNo(), PAYABLE_CENT, "MOCK", "PAY-RETURN-POINTS-1"));
        assertThat(payCallbackUseCase.handle(
                signedCallback(payment.txnNo(), order.orderNo(), PAYABLE_CENT)).status())
                .isEqualTo("SUCCESS");
        assertThat(statusOf(order.orderNo())).isEqualTo("WAIT_SHIP");
        return order.orderNo();
    }

    private void processRefund(String refundNo, String orderNo, long amountCent) {
        Long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
        refundCallbackService.process(new RefundOrder(refundNo, orderNo, orderId == null ? 0L : orderId,
                amountCent, "REFUND", "客户申请退款", RefundStatus.REFUNDING, System.currentTimeMillis()));
    }

    private long pointsBalanceOf() {
        Long value = jdbc.queryForObject("SELECT points_balance FROM customer WHERE id = ?",
                Long.class, CUSTOMER_ID);
        return value == null ? 0L : value;
    }

    private void seedCustomer() {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, password_hash, nickname, type, "
                        + "level_code, growth_value, points_balance, status, token_version, deleted) "
                        + "VALUES (?, 'CUS-8841', '13800008841', 'P@ssw0rd', '退款退积分客户', 'PERSON', "
                        + "'NORMAL', 0, ?, 'ACTIVE', 0, 0)",
                CUSTOMER_ID, INITIAL_POINTS);
    }
}
