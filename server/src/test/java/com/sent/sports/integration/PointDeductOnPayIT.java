package com.sent.sports.integration;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.service.CustomerPointService;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付成功扣减积分的验收（P5 闭环第 2 步）。
 *
 * <p><b>缺陷背景</b>：结算页与订单表都记了 {@code point_deduct_cent}，但支付回调从未真正扣减
 * {@code customer.points_balance}——用户可以拿同一批积分无限次抵扣。</p>
 *
 * <p><b>验收口径</b>（三条都必须成立）：
 * <ol>
 *   <li>支付回调成功后 {@code customer.points_balance} 真实减少 {@code point_deduct_cent}，
 *       且 {@code point_log} 出现一条 {@code type=USE} 的负向流水、{@code source_id}=订单号；</li>
 *   <li>并发扣减绝不会把余额扣成负数：可用条件更新 {@code points_balance >= used} 兜底，
 *       成功次数恒等于「余额 / 单次扣减」；</li>
 *   <li>余额不足（并发被花掉 / 脏数据）时回调<b>失败</b>，订单退回待付款、余额不动——
 *       宁可支付失败也不能白送抵扣。</li>
 * </ol>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class PointDeductOnPayIT extends FullStackIntegrationTest {

    private static final long CUSTOMER_ID = 8831L;
    private static final long SKU_ID = 9321L;
    /** 商品 100 元 → 30% 上限 = 3000 分。 */
    private static final long GOODS_CENT = 10_000L;
    private static final long CAP_CENT = 3_000L;
    private static final long PAYABLE_CENT = GOODS_CENT - CAP_CENT;
    private static final long INITIAL_POINTS = 50_000L;

    @Autowired
    private CreateOrderUseCase createOrderUseCase;
    @Autowired
    private CreatePaymentUseCase createPaymentUseCase;
    @Autowired
    private PayCallbackUseCase payCallbackUseCase;
    @Autowired
    private CustomerPointService customerPointService;

    @Test
    @DisplayName("支付回调扣减积分：余额真实减少、point_log 落一条 USE 流水并关联订单号")
    void payCallbackDeductsPoints() {
        seedCustomer(INITIAL_POINTS);
        seedSku(SKU_ID, GOODS_CENT, 10);
        String orderNo = createOrder("IDEM-POINT-1", (int) INITIAL_POINTS);

        assertThat(pointDeductCentOf(orderNo)).isEqualTo(CAP_CENT);
        assertThat(payableCentOf(orderNo)).isEqualTo(PAYABLE_CENT);
        assertThat(pointsBalanceOf()).as("下单只记抵扣额，不动余额").isEqualTo(INITIAL_POINTS);

        pay(orderNo, "PAY-POINT-1");

        assertThat(statusOf(orderNo)).isEqualTo("WAIT_SHIP");
        assertThat(pointsBalanceOf()).as("支付成功必须真实扣减").isEqualTo(INITIAL_POINTS - CAP_CENT);
        assertThat(countOf("point_log")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT type FROM point_log WHERE source_id = ?",
                String.class, orderNo)).isEqualTo("USE");
        assertThat(jdbc.queryForObject("SELECT change_value FROM point_log WHERE source_id = ?",
                Long.class, orderNo)).isEqualTo(-CAP_CENT);
        assertThat(jdbc.queryForObject("SELECT balance_after FROM point_log WHERE source_id = ?",
                Long.class, orderNo)).as("流水余额快照要与真实余额一致").isEqualTo(INITIAL_POINTS - CAP_CENT);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM point_log WHERE source_id = ?",
                Long.class, orderNo)).isEqualTo(CUSTOMER_ID);
    }

    @Test
    @DisplayName("同一订单重复回调：只扣一次，余额不会再变")
    void duplicateCallbackDeductsOnce() {
        seedCustomer(INITIAL_POINTS);
        seedSku(SKU_ID, GOODS_CENT, 10);
        String orderNo = createOrder("IDEM-POINT-2", (int) INITIAL_POINTS);
        pay(orderNo, "PAY-POINT-2");

        // 二次回调走「订单已是待发货」的幂等分支，本就不该再进扣减；
        // 这里额外直接重放一次台账，确认 uk_point_source 仍然只放行一条 USE 流水。
        customerPointService.deductPoints(CUSTOMER_ID, CAP_CENT, "ORDER_USE", orderNo);

        assertThat(countOf("point_log")).isEqualTo(1);
        assertThat(pointsBalanceOf()).isEqualTo(INITIAL_POINTS - CAP_CENT);
    }

    @Test
    @DisplayName("20 线程并发扣减：成功恒为 余额/单次，余额永不扣成负数")
    void concurrentDeductNeverGoesNegative() throws Exception {
        final long each = 1_000L;
        final int threads = 20;
        final long balance = 5_000L;       // 只够 5 次
        seedCustomer(balance);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final String sourceId = "SO-RACE-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        customerPointService.deductPoints(CUSTOMER_ID, each, "ORDER_USE", sourceId);
                        return "SUCCESS";
                    } catch (BizException e) {
                        return e.getErrorCode() == ErrorCode.POINT_NOT_ENOUGH
                                ? "POINT_NOT_ENOUGH" : "OTHER:" + e.getErrorCode();
                    }
                }));
            }
            start.countDown();

            int success = 0;
            int insufficient = 0;
            List<String> unexpected = new ArrayList<>();
            for (Future<String> f : futures) {
                String outcome = f.get(60, TimeUnit.SECONDS);
                if ("SUCCESS".equals(outcome)) {
                    success++;
                } else if ("POINT_NOT_ENOUGH".equals(outcome)) {
                    insufficient++;
                } else {
                    unexpected.add(outcome);
                }
            }
            assertThat(unexpected).as("只允许「积分不足」这一种失败").isEmpty();
            assertThat(success).as("5000 积分只够 5 次 1000").isEqualTo(5);
            assertThat(insufficient).isEqualTo(threads - 5);
        } finally {
            pool.shutdownNow();
        }

        assertThat(pointsBalanceOf()).as("余额必须刚好扣到 0，不能为负").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer WHERE id = ? AND points_balance < 0",
                Integer.class, CUSTOMER_ID)).isZero();
        // 失败的那些调用整体回滚：占位流水不能留在账上
        assertThat(countOf("point_log")).isEqualTo(5);
    }

    @Test
    @DisplayName("余额不足时支付回调失败：订单退回待付款、余额不动、不落流水")
    void insufficientBalanceBlocksCallback() {
        seedCustomer(100L);                 // 实际只有 100 积分
        seedSku(SKU_ID, GOODS_CENT, 10);
        // 下单时客户端声称有 50000 积分（并发/脏数据的典型场景），订单照样带着 3000 抵扣额落地
        String orderNo = createOrder("IDEM-POINT-3", (int) INITIAL_POINTS);
        assertThat(pointDeductCentOf(orderNo)).isEqualTo(CAP_CENT);

        CreatePaymentResult payment = createPaymentUseCase.create(
                new CreatePaymentCmd(orderNo, PAYABLE_CENT, "MOCK", "PAY-POINT-3"));
        BizException e = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> payCallbackUseCase.handle(
                        signedCallback(payment.txnNo(), orderNo, PAYABLE_CENT)));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POINT_NOT_ENOUGH);
        assertThat(statusOf(orderNo)).as("回调事务整体回滚，订单必须还在待付款").isEqualTo("WAIT_PAY");
        assertThat(payStatusOf(orderNo)).isEqualTo("UNPAID");
        assertThat(pointsBalanceOf()).as("余额不足时不得扣成负数").isEqualTo(100L);
        assertThat(countOf("point_log")).as("回滚后占位流水不能残留").isZero();
    }

    // ---------------------------------------------------------------- helpers

    private String createOrder(String idemKey, int availablePoints) {
        CreateOrderResult order = createOrderUseCase.create(new CreateOrderCmd(
                CUSTOMER_ID, seedAddress(CUSTOMER_ID), idemKey, availablePoints,
                Integer.MAX_VALUE, 0, List.of(line(SKU_ID, 1))));
        return order.orderNo();
    }

    private void pay(String orderNo, String payIdemKey) {
        CreatePaymentResult payment = createPaymentUseCase.create(
                new CreatePaymentCmd(orderNo, PAYABLE_CENT, "MOCK", payIdemKey));
        assertThat(payCallbackUseCase.handle(
                signedCallback(payment.txnNo(), orderNo, PAYABLE_CENT)).status())
                .isEqualTo("SUCCESS");
    }

    private long pointDeductCentOf(String orderNo) {
        Long value = jdbc.queryForObject("SELECT point_deduct_cent FROM orders WHERE order_no = ?",
                Long.class, orderNo);
        return value == null ? 0L : value;
    }

    private long payableCentOf(String orderNo) {
        Long value = jdbc.queryForObject("SELECT payable_cent FROM orders WHERE order_no = ?",
                Long.class, orderNo);
        return value == null ? 0L : value;
    }

    private long pointsBalanceOf() {
        Long value = jdbc.queryForObject("SELECT points_balance FROM customer WHERE id = ?",
                Long.class, CUSTOMER_ID);
        return value == null ? 0L : value;
    }

    private void seedCustomer(long pointsBalance) {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, password_hash, nickname, type, "
                        + "level_code, growth_value, points_balance, status, token_version, deleted) "
                        + "VALUES (?, 'CUS-8831', '13800008831', 'P@ssw0rd', '积分客户', 'PERSON', "
                        + "'NORMAL', 0, ?, 'ACTIVE', 0, 0)",
                CUSTOMER_ID, pointsBalance);
    }
}
