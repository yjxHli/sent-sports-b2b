package com.sent.sports.integration;

import com.sent.sports.report.mapper.SalesTrendMapper;
import com.sent.sports.report.mapper.po.SalesTrendRow;
import com.sent.sports.support.BaseIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 销售趋势 SQL 口径真库校验（批 4，持久层切片）：
 * 以 paid_at 为时间基准、按 paid_cent 聚合，只计 PAID / PART_REFUNDED，
 * 全额退款 FULL_REFUNDED 整单剔除，未支付与窗口外订单一并排除。
 *
 * <p>本测试只注入 {@code @Mapper}（由 @MapperScan 装配），不依赖任何 @Service，
 * 与 {@code PersistenceTestApplication} 持久层切片的装配范围一致。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class SalesTrendMapperIT extends BaseIntegrationTest {

    @Autowired
    private SalesTrendMapper salesTrendMapper;

    private void insertOrder(String orderNo, String payStatus, long payableCent, long paidCent,
                             LocalDateTime paidAt) {
        jdbc.update("INSERT INTO orders "
                        + "(order_no, customer_id, status, pay_status, payable_cent, paid_cent, "
                        + "paid_at, created_at, idem_key) "
                        + "VALUES (?, 9002, 'WAIT_SHIP', ?, ?, ?, ?, ?, ?)",
                orderNo, payStatus, payableCent, paidCent, paidAt,
                paidAt == null ? LocalDateTime.of(2026, 11, 8, 10, 0) : paidAt,
                "idem-" + orderNo);
    }

    @Test
    @DisplayName("按 paid_at 分日 SUM(paid_cent)：PAID/PART_REFUNDED 计入，FULL_REFUNDED 与未支付整单剔除")
    void aggregatesOnlyPaidOrdersByPaidAt() {
        LocalDateTime d7 = LocalDateTime.of(2026, 11, 7, 9, 30);
        LocalDateTime d8 = LocalDateTime.of(2026, 11, 8, 15, 20);

        // 两笔有效成交（payable_cent 故意与 paid_cent 不同，验证严格按 paid_cent 聚合）
        insertOrder("IT-TREND-1", "PAID", 99999L, 30000L, d7);
        insertOrder("IT-TREND-2", "PAID", 88888L, 20000L, d7);
        // 部分退款：计入
        insertOrder("IT-TREND-3", "PART_REFUNDED", 77777L, 15000L, d8);
        // 全额退款：整单剔除（金额刻意做大，若误计入会明显放大结果）
        insertOrder("IT-TREND-4", "FULL_REFUNDED", 66666L, 990000L, d8);
        // 未支付：剔除
        insertOrder("IT-TREND-5", "UNPAID", 55555L, 5000L, d8);
        // 已支付但 paid_at 为 NULL：严格口径下不落入任何一天
        insertOrder("IT-TREND-6", "PAID", 44444L, 7000L, null);
        // 窗口外的已支付：剔除
        insertOrder("IT-TREND-7", "PAID", 33333L, 8000L, LocalDateTime.of(2026, 10, 1, 10, 0));

        List<SalesTrendRow> rows = salesTrendMapper.selectDailyTrend(
                LocalDateTime.of(2026, 11, 7, 0, 0),
                LocalDateTime.of(2026, 11, 10, 0, 0));

        assertThat(rows).hasSize(2);

        assertThat(rows.get(0).getDay()).isEqualTo("2026-11-07");
        assertThat(rows.get(0).getAmountCent()).isEqualTo(50000L);
        assertThat(rows.get(0).getOrderCount()).isEqualTo(2);

        assertThat(rows.get(1).getDay()).isEqualTo("2026-11-08");
        assertThat(rows.get(1).getAmountCent()).isEqualTo(15000L);
        assertThat(rows.get(1).getOrderCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("窗口边界：from 含、to 不含，边界外订单不混入")
    void respectsHalfOpenTimeWindow() {
        insertOrder("IT-TREND-EDGE-1", "PAID", 10000L, 10000L,
                LocalDateTime.of(2026, 11, 6, 23, 59, 59));
        insertOrder("IT-TREND-EDGE-2", "PAID", 20000L, 20000L,
                LocalDateTime.of(2026, 11, 7, 0, 0));
        insertOrder("IT-TREND-EDGE-3", "PAID", 30000L, 30000L,
                LocalDateTime.of(2026, 11, 10, 0, 0));

        List<SalesTrendRow> rows = salesTrendMapper.selectDailyTrend(
                LocalDateTime.of(2026, 11, 7, 0, 0),
                LocalDateTime.of(2026, 11, 10, 0, 0));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDay()).isEqualTo("2026-11-07");
        assertThat(rows.get(0).getAmountCent()).isEqualTo(20000L);
    }
}
