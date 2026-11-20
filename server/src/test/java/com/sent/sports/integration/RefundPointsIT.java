package com.sent.sports.integration;

import com.sent.sports.member.service.MemberRefundGateway;
import com.sent.sports.order.service.CreateOrderUseCase;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退款扣回积分的验收（P0-R4）：退款回调必须<b>同步</b>真实扣回积分。
 *
 * <p><b>缺陷背景（审计 R4）</b>：{@code MemberRefundGatewayImpl} 原先只
 * {@code eventPublisher.publish(new MemberRefundEvent(...))}，而全仓没有订阅者也没有异步分发，
 * 事件发出去连异常都没有；退款回调却把这一步当成「积分已回退」继续往下走——
 * 结果是钱退了、积分没扣，账面完全看不出来。</p>
 *
 * <p><b>验收口径</b>：退款回调完成后 {@code point_log} 必须出现一条负向 REFUND 流水，
 * 且 {@code customer.points_balance} 同步减少；同一退款单重放不二次扣回。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class RefundPointsIT extends FullStackIntegrationTest {

    private static final long CUSTOMER_ID = 8821L;
    private static final long SKU_ID = 9311L;
    private static final int QTY = 1;
    private static final long PAYABLE_CENT = 20_000L;   // 200 元
    private static final long INITIAL_POINTS = 500L;
    private static final long EXPECTED_POINTS = INITIAL_POINTS - PAYABLE_CENT / 100;
    private static final String REFUND_NO = "RF-REFUND-POINTS-1";

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
    @DisplayName("退款回调同步扣回积分：point_log 出现一条负向 REFUND 流水")
    void refundCallbackDeductsPointsSynchronously() {
        String orderNo = seedPaidOrder();

        RefundOrder refund = new RefundOrder(REFUND_NO, orderNo, orderIdOf(orderNo), PAYABLE_CENT,
                "REFUND", "客户申请退款", RefundStatus.REFUNDING, System.currentTimeMillis());
        refundCallbackService.process(refund);

        assertThat(jdbc.queryForObject("SELECT status FROM refund_order WHERE refund_no = ?",
                String.class, REFUND_NO)).isEqualTo("REFUNDED");

        assertThat(countOf("point_log")).as("退款成功必须落一条积分扣回流水").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT change_value FROM point_log WHERE source_id = ?",
                Long.class, REFUND_NO)).isEqualTo(-(PAYABLE_CENT / 100));
        assertThat(jdbc.queryForObject("SELECT type FROM point_log WHERE source_id = ?",
                String.class, REFUND_NO)).isEqualTo("REFUND");
        assertThat(jdbc.queryForObject("SELECT customer_id FROM point_log WHERE source_id = ?",
                Long.class, REFUND_NO)).isEqualTo(CUSTOMER_ID);
        assertThat(jdbc.queryForObject("SELECT points_balance FROM customer WHERE id = ?",
                Long.class, CUSTOMER_ID)).as("积分余额必须同步扣减").isEqualTo(EXPECTED_POINTS);
    }

    @Test
    @DisplayName("同一退款单重复扣回（渠道重复通知 / 人工重试）：uk_point_source 兜底，只扣一次")
    void replayDoesNotDeductTwice() {
        seedCustomer();
        // 退款状态已是终态时重放会先被状态机挡住，因此这里直接验「账本」这一层：
        // 同一退款单号连扣两次，唯一键只放行第一条流水
        for (int i = 0; i < 2; i++) {
            memberRefundGateway.refundPoints(CUSTOMER_ID, REFUND_NO, PAYABLE_CENT);
        }

        assertThat(countOf("point_log")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT points_balance FROM customer WHERE id = ?",
                Long.class, CUSTOMER_ID)).isEqualTo(EXPECTED_POINTS);
    }

    /** 造一个「已支付、待发货」的真实订单（下单 → 发起支付 → 渠道签名回调）。 */
    private String seedPaidOrder() {
        seedCustomer();
        seedSku(SKU_ID, PAYABLE_CENT, 10);

        CreateOrderResult order = createOrderUseCase.create(
                new com.sent.sports.order.service.dto.CreateOrderCmd(CUSTOMER_ID, seedAddress(CUSTOMER_ID),
                        "IDEM-REFUND-POINTS-1", 0, Integer.MAX_VALUE, 0,
                        java.util.List.of(line(SKU_ID, QTY))));
        CreatePaymentResult payment = createPaymentUseCase.create(
                new CreatePaymentCmd(order.orderNo(), PAYABLE_CENT, "MOCK", "PAY-REFUND-POINTS-1"));
        assertThat(payCallbackUseCase.handle(
                signedCallback(payment.txnNo(), order.orderNo(), PAYABLE_CENT)).status())
                .isEqualTo("SUCCESS");
        assertThat(statusOf(order.orderNo())).isEqualTo("WAIT_SHIP");
        return order.orderNo();
    }

    private long orderIdOf(String orderNo) {
        Long id = jdbc.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
        return id == null ? 0L : id;
    }

    private void seedCustomer() {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, password_hash, nickname, type, "
                        + "level_code, growth_value, points_balance, status, token_version, deleted) "
                        + "VALUES (?, 'CUS-8821', '13800008821', 'P@ssw0rd', '退款客户', 'PERSON', "
                        + "'NORMAL', 0, ?, 'ACTIVE', 0, 0)",
                CUSTOMER_ID, INITIAL_POINTS);
    }
}
