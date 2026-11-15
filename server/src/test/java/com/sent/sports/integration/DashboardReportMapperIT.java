package com.sent.sports.integration;

import com.sent.sports.report.mapper.DashboardReportMapper;
import com.sent.sports.report.mapper.po.SalesCountRow;
import com.sent.sports.report.mapper.po.SalesRankRow;
import com.sent.sports.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 销售业绩排行 / 工作台看板 SQL 口径真库校验。
 *
 * <p>与 {@code SalesTrendMapperIT} 同一把尺子：以 {@code paid_at} 为时间基准、
 * 按 {@code paid_cent} 聚合，只计 PAID / PART_REFUNDED，全额退款与未支付整单剔除。
 * 每个用例前由 {@link BaseIntegrationTest} 清空全部业务表，互不污染。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class DashboardReportMapperIT extends BaseIntegrationTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 8, 0, 0);
    private static final LocalDateTime NEXT_DAY = LocalDateTime.of(2026, 11, 9, 0, 0);

    @Autowired
    private DashboardReportMapper mapper;

    private void staff(long id, String name) {
        jdbc.update("INSERT INTO sys_staff (id, staff_no, name, phone, password_hash, role_id) "
                        + "VALUES (?, ?, ?, ?, ?, 1)",
                id, "IT" + id, name, "1380000" + id, "hash");
    }

    private void order(String no, Long owner, String payStatus, long paidCent,
                       LocalDateTime paidAt, String status) {
        jdbc.update("INSERT INTO orders "
                        + "(order_no, customer_id, owner_staff_id, status, pay_status, "
                        + "payable_cent, paid_cent, paid_at, created_at, idem_key) "
                        + "VALUES (?, 9002, ?, ?, ?, ?, ?, ?, ?, ?)",
                no, owner, status, payStatus, paidCent, paidCent, paidAt,
                LocalDateTime.of(2026, 11, 8, 10, 0), "idem-" + no);
    }

    @Test
    @DisplayName("排行按归属销售聚合：剔除全额退款/未支付/窗口外，按成交金额降序，无归属兜底")
    void salesRankAggregatesByOwner() {
        staff(9001L, "张三");
        staff(9005L, "李四");
        LocalDateTime paid = LocalDateTime.of(2026, 11, 8, 10, 0);

        order("IT-RANK-1", 9001L, "PAID", 30000L, paid, "WAIT_SHIP");
        order("IT-RANK-2", 9001L, "PART_REFUNDED", 10000L, paid, "WAIT_SHIP");
        order("IT-RANK-3", 9005L, "PAID", 20000L, paid, "WAIT_SHIP");
        // 全额退款金额刻意做大，误计入会明显放大结果
        order("IT-RANK-4", 9001L, "FULL_REFUNDED", 990000L, paid, "WAIT_SHIP");
        order("IT-RANK-5", 9005L, "UNPAID", 5000L, paid, "WAIT_PAY");
        order("IT-RANK-6", null, "PAID", 7000L, paid, "WAIT_SHIP");
        order("IT-RANK-7", 9001L, "PAID", 8000L,
                LocalDateTime.of(2026, 10, 1, 10, 0), "WAIT_SHIP");

        List<SalesRankRow> rows = mapper.selectSalesRank(DAY, NEXT_DAY);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getStaffId()).isEqualTo(9001L);
        assertThat(rows.get(0).getStaffName()).isEqualTo("张三");
        assertThat(rows.get(0).getAmountCent()).isEqualTo(40000L);
        assertThat(rows.get(0).getOrderCount()).isEqualTo(2);
        assertThat(rows.get(0).getCustomerCount()).isEqualTo(1);
        assertThat(rows.get(1).getStaffName()).isEqualTo("李四");
        assertThat(rows.get(1).getAmountCent()).isEqualTo(20000L);
        assertThat(rows.get(2).getStaffId()).isNull();
        assertThat(rows.get(2).getStaffName()).isEqualTo("未分配");
        assertThat(rows.get(2).getAmountCent()).isEqualTo(7000L);
    }

    @Test
    @DisplayName("今日汇总只统计窗口内已支付订单，七项待办计数与真实状态一致")
    void dashboardCountsMatchRows() {
        LocalDateTime paid = LocalDateTime.of(2026, 11, 8, 10, 0);
        order("IT-DASH-1", 9001L, "PAID", 30000L, paid, "WAIT_SHIP");
        order("IT-DASH-2", 9001L, "PART_REFUNDED", 20000L, paid, "PART_SHIPPED");
        order("IT-DASH-3", 9001L, "FULL_REFUNDED", 990000L, paid, "WAIT_RECEIVE");
        order("IT-DASH-4", 9001L, "UNPAID", 5000L, paid, "WAIT_PAY");
        order("IT-DASH-5", 9001L, "PAID", 8000L,
                LocalDateTime.of(2026, 10, 1, 10, 0), "COMPLETED");

        SalesCountRow sales = mapper.selectSales(DAY, NEXT_DAY);
        assertThat(sales.getOrderCount()).isEqualTo(2L);
        assertThat(sales.getAmountCent()).isEqualTo(50000L);

        jdbc.update("INSERT INTO custom_order (custom_no, customer_id, qty, status) "
                + "VALUES ('IT-C-1', 9002, 1, 'WAIT_REVIEW')");
        jdbc.update("INSERT INTO custom_order (custom_no, customer_id, qty, status) "
                + "VALUES ('IT-C-2', 9002, 1, 'WAIT_DRAFT')");
        jdbc.update("INSERT INTO chat_session (customer_id, status) VALUES (9002, 'WAITING')");
        jdbc.update("INSERT INTO chat_session (customer_id, status) VALUES (9003, 'CLOSED')");
        jdbc.update("INSERT INTO sku_stock (sku_id, physical_qty, safety_qty) VALUES (99001, 2, 10)");
        jdbc.update("INSERT INTO sku_stock (sku_id, physical_qty, safety_qty) VALUES (99002, 20, 10)");
        jdbc.update("INSERT INTO refund_order "
                + "(refund_no, order_id, type, amount_cent, status) VALUES ('IT-R-1', 1, 'REFUND', 1000, 'PENDING')");
        jdbc.update("INSERT INTO refund_order "
                + "(refund_no, order_id, type, amount_cent, status) VALUES ('IT-R-2', 2, 'REFUND', 1000, 'REFUNDED')");
        jdbc.update("INSERT INTO receivable "
                + "(order_id, customer_id, amount_cent, status) VALUES (1, 9002, 1000, 'OVERDUE')");
        jdbc.update("INSERT INTO receivable "
                + "(order_id, customer_id, amount_cent, status) VALUES (2, 9002, 1000, 'NORMAL')");

        assertThat(mapper.countPendingShip()).isEqualTo(2L);
        assertThat(mapper.countPendingCustomReview()).isEqualTo(1L);
        assertThat(mapper.countWaitingChat()).isEqualTo(1L);
        assertThat(mapper.countWarningSku()).isEqualTo(1L);
        assertThat(mapper.countPendingRefund()).isEqualTo(1L);
        assertThat(mapper.countOverdueReceivable()).isEqualTo(1L);
    }
}
