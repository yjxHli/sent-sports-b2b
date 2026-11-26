package com.sent.sports.integration;

import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 定制图稿上传超时的真库口径（2026-11-23 用户口径）：
 * 1) 只扫「WAIT_DRAFT + 订单已支付」——未支付单归 OrderTimeoutJob，已传稿的单不参与；
 * 2) 扫描按支付时间升序（最早快超时的先处理）；
 * 3) 提醒落痕与取消都是条件更新，重复执行影响 0 行（幂等）。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class CustomizeDraftTimeoutMapperIT extends FullStackIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0);

    /** 存量豁免线取很早的时刻：这些用例只关心扫描口径本身。 */
    private static final LocalDateTime SCAN_FROM = LocalDateTime.of(2020, 1, 1, 0, 0);

    @Autowired
    private CustomOrderMapper mapper;

    @Test
    @DisplayName("扫描口径：只取待上传且已支付的定制单，按支付时间升序")
    void selectsOnlyPaidWaitingDraftsOrderedByPaidAt() {
        insertCustom("CU-T-1", "SO-T-1", "PAID", LocalDateTime.of(2026, 11, 21, 9, 0), "WAIT_DRAFT");
        insertCustom("CU-T-2", "SO-T-2", "PAID", LocalDateTime.of(2026, 11, 20, 9, 0), "WAIT_DRAFT");
        // 未支付：归 OrderTimeoutJob 处理，不该出现在这里
        insertCustom("CU-T-3", "SO-T-3", "UNPAID", null, "WAIT_DRAFT");
        // 已提交图稿：用户已经传过，不参与超时取消
        insertCustom("CU-T-4", "SO-T-4", "PAID", LocalDateTime.of(2026, 11, 19, 9, 0), "WAIT_REVIEW");

        List<CustomOrderMapper.DraftWaitingRow> rows = mapper.selectDraftWaiting(SCAN_FROM, 200);

        assertThat(rows).extracting(CustomOrderMapper.DraftWaitingRow::getCustomNo)
                .containsExactly("CU-T-2", "CU-T-1");
        assertThat(rows.get(0).getPaidAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 9, 0));
        assertThat(rows.get(0).getCustomerId()).isEqualTo(9002L);
        assertThat(rows.get(0).getRemindCount()).isZero();
        assertThat(rows.get(0).getLastRemindAt()).isNull();
    }

    @Test
    @DisplayName("存量豁免线：早于 paidFrom 支付的老单不返回，避免规则上线瞬间批量退款")
    void skipsOrdersPaidBeforeEnforceFrom() {
        LocalDateTime enforceFrom = LocalDateTime.of(2026, 11, 23, 0, 0);
        insertCustom("CU-E-1", "SO-E-1", "PAID", enforceFrom.minusMinutes(1), "WAIT_DRAFT");
        insertCustom("CU-E-2", "SO-E-2", "PAID", enforceFrom, "WAIT_DRAFT");

        List<String> customNos = mapper.selectDraftWaiting(enforceFrom, 200).stream()
                .map(CustomOrderMapper.DraftWaitingRow::getCustomNo).toList();

        // 边界含等号：等于上线时刻的单仍要纳入
        assertThat(customNos).containsExactly("CU-E-2");
    }

    @Test
    @DisplayName("提醒落痕：同一 12 小时窗口内只成功一次，跨窗口后可以再提醒")
    void markingReminderIsIdempotentWithinTwelveHours() {
        insertCustom("CU-R-1", "SO-R-1", "PAID", NOW.minusHours(13), "WAIT_DRAFT");

        // 首次提醒：抢到资格
        assertThat(mapper.markDraftReminded("CU-R-1", NOW, NOW.minusHours(12))).isEqualTo(1);
        // 同一窗口内再来一次：条件不满足，影响 0 行（不会重复发信）
        assertThat(mapper.markDraftReminded("CU-R-1", NOW.plusMinutes(5), NOW.plusMinutes(5).minusHours(12)))
                .isZero();
        // 过了 12 小时：可以再提醒
        assertThat(mapper.markDraftReminded("CU-R-1", NOW.plusHours(13), NOW.plusHours(1))).isEqualTo(1);

        CustomOrderMapper.DraftWaitingRow row = mapper.selectDraftWaiting(SCAN_FROM, 200).get(0);
        assertThat(row.getRemindCount()).isEqualTo(2);
        assertThat(row.getLastRemindAt()).isEqualTo(NOW.plusHours(13));
    }

    @Test
    @DisplayName("提醒不给已提交图稿的单落痕（条件带 status='WAIT_DRAFT'）")
    void doesNotMarkReminderForSubmittedDraft() {
        insertCustom("CU-R-2", "SO-R-2", "PAID", NOW.minusHours(13), "WAIT_REVIEW");

        assertThat(mapper.markDraftReminded("CU-R-2", NOW, NOW.minusHours(12))).isZero();
    }

    @Test
    @DisplayName("取消是条件迁移：WAIT_DRAFT 只能取消一次，WAIT_REVIEW 一律拒绝")
    void cancelIsConditionalTransition() {
        insertCustom("CU-C-1", "SO-C-1", "PAID", NOW.minusHours(80), "WAIT_DRAFT");
        insertCustom("CU-C-2", "SO-C-2", "PAID", NOW.minusHours(80), "WAIT_REVIEW");

        assertThat(mapper.transitStatus("CU-C-1", "WAIT_DRAFT", "CANCELLED")).isEqualTo(1);
        assertThat(mapper.transitStatus("CU-C-1", "WAIT_DRAFT", "CANCELLED")).isZero();
        assertThat(mapper.transitStatus("CU-C-2", "WAIT_DRAFT", "CANCELLED")).isZero();
        assertThat(mapper.selectByCustomNo("CU-C-1").getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("详情引用带出支付时间：倒计时以支付时间为起算点")
    void orderRefCarriesPaidAt() {
        insertCustom("CU-D-1", "SO-D-1", "PAID", LocalDateTime.of(2026, 11, 20, 10, 0), "WAIT_DRAFT");

        CustomOrderMapper.OrderRefRow ref = mapper.selectOrderRef("CU-D-1");

        assertThat(ref.getOrderNo()).isEqualTo("SO-D-1");
        assertThat(ref.getPayStatus()).isEqualTo("PAID");
        assertThat(ref.getPaidAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 10, 0));
    }

    /** 造一张「订单 + 定制行 + 定制单」；paidAt 为 null 时按未支付落库。 */
    private void insertCustom(String customNo, String orderNo, String payStatus,
                              LocalDateTime paidAt, String customStatus) {
        String orderStatus = "UNPAID".equals(payStatus) ? "WAIT_PAY" : "WAIT_SHIP";
        jdbc.update("INSERT INTO orders (order_no, customer_id, status, pay_status, goods_amount_cent, "
                        + "payable_cent, paid_cent, paid_at, idem_key) VALUES (?, 9002, ?, ?, 5000, 5000, ?, ?, ?)",
                orderNo, orderStatus, payStatus, paidAt == null ? 0L : 5000L, paidAt, "IDEM-" + orderNo);
        Long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
        jdbc.update("INSERT INTO order_item (order_id, sku_id, qty, unit_price_cent, amount_cent, "
                        + "discount_alloc_cent, shipped_qty, custom_flag, custom_fee_cent) "
                        + "VALUES (?, 9101, 1, 5000, 5000, 0, 0, 1, 1000)",
                orderId);
        Long itemId = jdbc.queryForObject(
                "SELECT id FROM order_item WHERE order_id = ? ORDER BY id DESC LIMIT 1", Long.class, orderId);
        jdbc.update("INSERT INTO custom_order (custom_no, order_item_id, customer_id, qty, fee_cent, status) "
                        + "VALUES (?, ?, 9002, 1, 1000, ?)",
                customNo, itemId, customStatus);
    }
}
