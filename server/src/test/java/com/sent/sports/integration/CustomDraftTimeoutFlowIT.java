package com.sent.sports.integration;

import com.sent.sports.customize.service.CustomDraftTimeoutQueryService;
import com.sent.sports.customize.service.CustomDraftTimeoutUseCase;
import com.sent.sports.job.CustomDraftTimeoutJob;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.order.service.CloseOrderUseCase;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.order.service.dto.OrderLineCmd;
import com.sent.sports.payment.service.CreatePaymentUseCase;
import com.sent.sports.payment.service.PayCallbackUseCase;
import com.sent.sports.payment.service.dto.CreatePaymentCmd;
import com.sent.sports.payment.service.dto.CreatePaymentResult;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 定制图稿 72 小时超时的<b>真链路</b>验收（2026-11-23 用户口径）。
 *
 * <p>与 {@code CustomDraftTimeoutMapperIT}（只验 SQL 口径）不同，这里把整条链跑通：
 * 真实下单 → 支付回调 → 定时任务 → 关单退款 → 券返还 → 站内信，断言的是<b>钱和单据的终态</b>。
 * 这条链涉及资金，单测用 mock 无法证明「券真的回到账户」「退款单真的落 REFUNDED」。</p>
 *
 * <p>任务实例在这里手工构造，{@code enforceFrom} 取 {@code 1970-01-01}：用例把
 * {@code paid_at} 改成 80 小时前，只有放开豁免线才会进入扫描范围。生产默认值（规则上线日）
 * 由 {@code CustomDraftTimeoutJobTest} 覆盖。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class CustomDraftTimeoutFlowIT extends FullStackIntegrationTest {

    private static final long CUSTOMER_ID = 8871L;
    private static final long SKU_ID = 9331L;
    private static final long GOODS_CENT = 10_000L;
    private static final String COUPON_NO = "CP-DRAFT-TIMEOUT-1";
    /** 生产配置里的存量豁免线语义：用例显式放开，让「80 小时前支付」的单进入扫描范围。 */
    private static final String ENFORCE_FROM_ALL = "1970-01-01T00:00:00";

    @Autowired
    private CreateOrderUseCase createOrderUseCase;
    @Autowired
    private CreatePaymentUseCase createPaymentUseCase;
    @Autowired
    private PayCallbackUseCase payCallbackUseCase;
    @Autowired
    private CustomDraftTimeoutQueryService draftQueryService;
    @Autowired
    private CustomDraftTimeoutUseCase draftTimeoutUseCase;
    @Autowired
    private CloseOrderUseCase closeOrderUseCase;
    @Autowired
    private OrderQueryService orderQueryService;
    @Autowired
    private MessageSendService messageSendService;

    @Test
    @DisplayName("支付满 72 小时未传图稿：订单关闭 + 全额退款 + 定制单取消 + 券退回 + 取消通知")
    void cancelsOrderRefundsAndReturnsCoupon() {
        String orderNo = seedPaidCustomOrder();
        String customNo = customNoOf(orderNo);
        long paidCent = paidCentOf(orderNo);
        long couponId = seedUsedCoupon(orderNo);
        // 超时判定只看 paid_at：把它推到 80 小时前即「已逾期」
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_no = ?",
                LocalDateTime.now().minusHours(80), orderNo);

        CustomDraftTimeoutJob.RunResult result = job().runOnce();

        assertThat(result.cancelled()).isEqualTo(1);
        assertThat(statusOf(orderNo)).as("订单进入终态").isEqualTo("CLOSED");
        assertThat(payStatusOf(orderNo)).as("支付维度必须全退").isEqualTo("FULL_REFUNDED");

        Long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
        assertThat(jdbc.queryForObject("SELECT amount_cent FROM refund_order WHERE order_id = ?", Long.class, orderId))
                .as("退款额 = 实付，一分不少").isEqualTo(paidCent);
        assertThat(jdbc.queryForObject("SELECT status FROM refund_order WHERE order_id = ?", String.class, orderId))
                .as("自动退款直接落终态，不走人工审核")
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT reason_code FROM refund_order WHERE order_id = ?", String.class, orderId))
                .isEqualTo("ADMIN_CLOSE");

        assertThat(customStatusOf(customNo)).isEqualTo("CANCELLED");

        // 券：状态、使用时间、关联订单三处都要复原，否则用户拿券下单会被「已使用」挡住
        assertThat(jdbc.queryForObject("SELECT status FROM customer_coupon WHERE id = ?", String.class, couponId))
                .isEqualTo("UNUSED");
        assertThat(jdbc.queryForObject("SELECT used_at FROM customer_coupon WHERE id = ?", LocalDateTime.class, couponId))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT order_no FROM customer_coupon WHERE id = ?", String.class, couponId))
                .isNull();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_message WHERE customer_id = ? AND biz_no = ? AND title LIKE ?",
                Integer.class, CUSTOMER_ID, customNo, "%自动取消%"))
                .as("取消通知必须落到站内信").isEqualTo(1);
    }

    @Test
    @DisplayName("支付满 12 小时：发一条带剩余时间的提醒；同一 12 小时窗口内重跑不再追发")
    void remindsOncePerTwelveHours() {
        String orderNo = seedPaidCustomOrder();
        String customNo = customNoOf(orderNo);
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_no = ?",
                LocalDateTime.now().minusHours(13), orderNo);

        CustomDraftTimeoutJob.RunResult first = job().runOnce();

        assertThat(first.reminded()).isEqualTo(1);
        assertThat(first.cancelled()).isZero();
        assertThat(customStatusOf(customNo)).as("只提醒、不动单").isEqualTo("WAIT_DRAFT");
        assertThat(statusOf(orderNo)).isEqualTo("WAIT_SHIP");

        String content = jdbc.queryForObject(
                "SELECT content FROM user_message WHERE customer_id = ? AND biz_no = ?", String.class,
                CUSTOMER_ID, customNo);
        assertThat(content).contains("剩余").contains("积分与优惠券");
        assertThat(jdbc.queryForObject("SELECT last_draft_remind_at FROM custom_order WHERE custom_no = ?",
                LocalDateTime.class, customNo)).isNotNull();

        // 任务每 5 分钟跑一轮，紧接着再跑一轮不能变成骚扰
        CustomDraftTimeoutJob.RunResult second = job().runOnce();

        assertThat(second.reminded()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_message WHERE biz_no = ?",
                Integer.class, customNo)).isEqualTo(1);
    }

    @Test
    @DisplayName("已上传图稿的定制单不进超时链路：既不提醒也不取消")
    void leavesSubmittedDraftsAlone() {
        String orderNo = seedPaidCustomOrder();
        String customNo = customNoOf(orderNo);
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_no = ?",
                LocalDateTime.now().minusHours(80), orderNo);
        jdbc.update("UPDATE custom_order SET status = 'WAIT_REVIEW' WHERE custom_no = ?", customNo);

        CustomDraftTimeoutJob.RunResult result = job().runOnce();

        assertThat(result.cancelled()).isZero();
        assertThat(result.reminded()).isZero();
        assertThat(statusOf(orderNo)).isEqualTo("WAIT_SHIP");
        assertThat(customStatusOf(customNo)).isEqualTo("WAIT_REVIEW");
        // 只断言与本单相关的消息：支付成功本身也会给用户发站内信
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_message WHERE biz_no = ?",
                Integer.class, customNo)).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private CustomDraftTimeoutJob job() {
        return new CustomDraftTimeoutJob(draftQueryService, draftTimeoutUseCase, closeOrderUseCase,
                orderQueryService, messageSendService, Clock.systemDefaultZone(), ENFORCE_FROM_ALL);
    }

    /** 下一笔「1 件 UV 定制件 + 支付成功」的真实订单（下单时即落 WAIT_DRAFT 定制单）。 */
    private String seedPaidCustomOrder() {
        seedCustomer();
        seedSku(SKU_ID, GOODS_CENT, 10);
        CreateOrderResult order = createOrderUseCase.create(new CreateOrderCmd(
                CUSTOMER_ID, seedAddress(CUSTOMER_ID), "IDEM-DRAFT-TIMEOUT-1", 0, Integer.MAX_VALUE, 0,
                List.of(new OrderLineCmd(SKU_ID, 1, "PERSON", 100, 100, false, List.of(), true))));
        long payable = payableCentOf(order.orderNo());
        CreatePaymentResult payment = createPaymentUseCase.create(
                new CreatePaymentCmd(order.orderNo(), payable, "MOCK", "PAY-DRAFT-TIMEOUT-1"));
        assertThat(payCallbackUseCase.handle(
                signedCallback(payment.txnNo(), order.orderNo(), payable)).status()).isEqualTo("SUCCESS");
        return order.orderNo();
    }

    private String customNoOf(String orderNo) {
        return jdbc.queryForObject("SELECT co.custom_no FROM custom_order co "
                + "JOIN order_item oi ON oi.id = co.order_item_id "
                + "JOIN orders o ON o.id = oi.order_id WHERE o.order_no = ?", String.class, orderNo);
    }

    private String customStatusOf(String customNo) {
        return jdbc.queryForObject("SELECT status FROM custom_order WHERE custom_no = ?", String.class, customNo);
    }

    private long payableCentOf(String orderNo) {
        Long value = jdbc.queryForObject("SELECT payable_cent FROM orders WHERE order_no = ?", Long.class, orderNo);
        return value == null ? 0L : value;
    }

    private long paidCentOf(String orderNo) {
        Long value = jdbc.queryForObject("SELECT paid_cent FROM orders WHERE order_no = ?", Long.class, orderNo);
        return value == null ? 0L : value;
    }

    /** 直接落一张「已被这单核销」的券：验证退款时 couponDeduction 能把券退回账户。 */
    private long seedUsedCoupon(String orderNo) {
        jdbc.update("INSERT INTO customer_coupon (customer_id, template_id, coupon_no, name, amount_cent, "
                        + "threshold_cent, status, source, expire_at, used_at, order_no) "
                        + "VALUES (?, 0, ?, '超时取消测试券', 500, 0, 'USED', 'ADMIN', ?, NOW(3), ?)",
                CUSTOMER_ID, COUPON_NO, LocalDateTime.now().plusDays(7), orderNo);
        Long id = jdbc.queryForObject("SELECT id FROM customer_coupon WHERE coupon_no = ?", Long.class, COUPON_NO);
        return id == null ? 0L : id;
    }

    private void seedCustomer() {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, password_hash, nickname, type, "
                        + "level_code, growth_value, points_balance, status, token_version, deleted) "
                        + "VALUES (?, 'CUS-8871', '13800008871', 'P@ssw0rd', '超时取消客户', 'PERSON', "
                        + "'NORMAL', 0, 0, 'ACTIVE', 0, 0)",
                CUSTOMER_ID);
    }
}
