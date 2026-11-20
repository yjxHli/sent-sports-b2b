package com.sent.sports.integration;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.job.AutoConfirmJob;
import com.sent.sports.order.service.ConfirmReceiptUseCase;
import com.sent.sports.order.service.dto.ConfirmReceiptCmd;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 自动确认收货的验收（P0-R1）：Job 必须走用例，而不是自己改状态。
 *
 * <p><b>缺陷背景（审计 R1）</b>：{@code AutoConfirmJob} 原先直接
 * {@code orderMapper.transitStatus(no, WAIT_RECEIVE, COMPLETED)}，只推进状态，
 * 不触发用例里的下游副作用——7 天未确认的订单因此拿不到积分与成长值，
 * 财务也永远收不到应收台账（事后再补要人工处理 {@code uk_receivable_order}）。</p>
 *
 * <p><b>验收口径</b>：Job 跑完后，该订单必须同时产生
 * ① {@code point_log} 一行、② {@code receivable}（应收台账）一行；
 * 并顺带核对成长值流水与客户余额，证明 Job 与「用户点确认收货」是同一条链路。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class AutoConfirmJobIT extends FullStackIntegrationTest {

    private static final long CUSTOMER_ID = 8811L;
    private static final long SKU_ID = 9301L;
    private static final int QTY = 2;
    private static final long UNIT_PRICE_CENT = 10_000L;
    private static final long PAYABLE_CENT = UNIT_PRICE_CENT * QTY;
    private static final long EXPECTED_POINTS = PAYABLE_CENT / 100;   // 1 元 = 1 积分
    private static final String ORDER_NO = "SO-AUTOCONFIRM-1";

    @Autowired
    private AutoConfirmJob autoConfirmJob;
    @Autowired
    private ConfirmReceiptUseCase confirmReceiptUseCase;

    @Test
    @DisplayName("超 7 天未确认的订单被自动确认，并真实产生积分流水与应收台账")
    void autoConfirmPostsPointsAndReceivable() {
        seedCustomer();
        seedAwaitingReceiveOrder();

        autoConfirmJob.runOnce();

        assertThat(statusOf(ORDER_NO)).as("自动确认必须把订单推进到 COMPLETED").isEqualTo("COMPLETED");

        // ① 积分台账：这是 R1 修复前完全缺失的一行
        assertThat(countOf("point_log")).as("订单完成必须落一条积分流水").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM point_log WHERE source_id = ?",
                Long.class, ORDER_NO)).isEqualTo(CUSTOMER_ID);
        assertThat(jdbc.queryForObject("SELECT change_value FROM point_log WHERE source_id = ?",
                Long.class, ORDER_NO)).isEqualTo(EXPECTED_POINTS);
        assertThat(jdbc.queryForObject("SELECT type FROM point_log WHERE source_id = ?",
                String.class, ORDER_NO)).isEqualTo("ORDER");
        assertThat(jdbc.queryForObject("SELECT balance_after FROM point_log WHERE source_id = ?",
                Long.class, ORDER_NO)).isEqualTo(EXPECTED_POINTS);
        assertThat(jdbc.queryForObject("SELECT points_balance FROM customer WHERE id = ?",
                Long.class, CUSTOMER_ID)).as("积分余额必须真实累加").isEqualTo(EXPECTED_POINTS);

        // ② 财务台账：一单一应收，金额等于订单应付
        assertThat(countOf("receivable")).as("订单完成必须生成应收台账").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_cent FROM receivable WHERE order_id = "
                + "(SELECT id FROM orders WHERE order_no = ?)", Long.class, ORDER_NO))
                .isEqualTo(PAYABLE_CENT);

        // ③ 成长值：与用户主动确认收货同一条链路
        assertThat(countOf("growth_log")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT growth_value FROM customer WHERE id = ?",
                Long.class, CUSTOMER_ID)).isEqualTo(EXPECTED_POINTS);
    }

    @Test
    @DisplayName("重复执行 / 重放确认收货都不会二次发积分、成长值与应收（幂等）")
    void repeatedRunIsIdempotent() {
        seedCustomer();
        seedAwaitingReceiveOrder();

        autoConfirmJob.runOnce();
        autoConfirmJob.runOnce();
        // 直接再调一次用例（等价于用户同时点了「确认收货」）：状态准入把它挡在门外
        assertThatThrownBy(() -> confirmReceiptUseCase.confirm(
                new ConfirmReceiptCmd(ORDER_NO, List.of())))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.STATE_CONFLICT);

        assertThat(countOf("point_log")).isEqualTo(1);
        assertThat(countOf("growth_log")).isEqualTo(1);
        assertThat(countOf("receivable")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT points_balance FROM customer WHERE id = ?",
                Long.class, CUSTOMER_ID)).isEqualTo(EXPECTED_POINTS);
    }

    private void seedCustomer() {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, password_hash, nickname, type, "
                        + "level_code, growth_value, points_balance, status, token_version, deleted) "
                        + "VALUES (?, 'CUS-8811', '13800008811', 'P@ssw0rd', '自动收货客户', 'PERSON', "
                        + "'NORMAL', 0, 0, 'ACTIVE', 0, 0)",
                CUSTOMER_ID);
    }

    /** 已发货待收货、且最后更新时间在 8 天前（超过 7 天阈值）的订单。 */
    private void seedAwaitingReceiveOrder() {
        jdbc.update("INSERT INTO orders (order_no, customer_id, status, pay_status, goods_amount_cent, "
                        + "payable_cent, paid_cent, version, idem_key) "
                        + "VALUES (?, ?, 'WAIT_RECEIVE', 'PAID', ?, ?, ?, 0, ?)",
                ORDER_NO, CUSTOMER_ID, PAYABLE_CENT, PAYABLE_CENT, PAYABLE_CENT, "IDEM-AUTOCONFIRM-1");
        Long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, ORDER_NO);
        jdbc.update("INSERT INTO order_item (order_id, sku_id, spu_title_snap, qty, unit_price_cent, "
                        + "price_source, amount_cent, shipped_qty, custom_flag, custom_fee_cent) "
                        + "VALUES (?, ?, '验收商品', ?, ?, 'RETAIL', ?, ?, 0, 0)",
                orderId, SKU_ID, QTY, UNIT_PRICE_CENT, PAYABLE_CENT, QTY);
        // 扫描条件是 updated_at <= now-7d：显式回写，覆盖 ON UPDATE CURRENT_TIMESTAMP
        jdbc.update("UPDATE orders SET updated_at = DATE_SUB(NOW(3), INTERVAL 8 DAY) WHERE order_no = ?",
                ORDER_NO);
    }
}
