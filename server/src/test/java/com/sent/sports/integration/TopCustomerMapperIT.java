package com.sent.sports.integration;

import com.sent.sports.common.mybatis.DataScopeContext;
import com.sent.sports.report.mapper.TopCustomerMapper;
import com.sent.sports.report.mapper.po.TopCustomerRow;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大客户采购排行真库验证（2026-11-21）：
 * 1) 口径：只算 PAID / PART_REFUNDED + paid_at 在窗口内的订单；
 * 2) 名称三层回落：企业名称 → 昵称 → 客户#{id}；
 * 3) 数据范围：SELF / TEAM / TEAM 无下属 / ALL。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class TopCustomerMapperIT extends FullStackIntegrationTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 10, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 12, 0, 0);

    @Autowired
    private TopCustomerMapper mapper;

    @AfterEach
    void clearScope() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("按 customer_id 聚合实付金额，未支付与窗口外不计；名称企业优先、再昵称、再兜底")
    void aggregatesPaidOrdersByCustomer() {
        insertCustomer(1L, "散客小张", "13800000001", "PERSON");
        insertCustomer(2L, "联系人王", "13800000002", "ENTERPRISE");
        jdbc.update("INSERT INTO enterprise_profile (customer_id, company_name, status) "
                + "VALUES (2, '森特供应链有限公司', 'ACTIVE')");

        insertOrder("SO-TC-A", 1L, 7L, "PAID", LocalDateTime.of(2026, 11, 11, 9, 0), 10_000L);
        insertOrder("SO-TC-B", 1L, 7L, "PAID", LocalDateTime.of(2026, 11, 11, 10, 0), 5_000L);
        insertOrder("SO-TC-C", 2L, 7L, "PAID", LocalDateTime.of(2026, 11, 11, 11, 0), 20_000L);
        insertOrder("SO-TC-D", 3L, 7L, "PART_REFUNDED", LocalDateTime.of(2026, 11, 11, 12, 0), 3_000L);
        insertOrder("SO-TC-E", 2L, 7L, "UNPAID", LocalDateTime.of(2026, 11, 11, 13, 0), 99_000L);
        insertOrder("SO-TC-F", 2L, 7L, "PAID", LocalDateTime.of(2026, 11, 1, 13, 0), 88_000L);

        DataScopeContext.set(DataScopeContext.Scope.all(9001L));
        List<TopCustomerRow> rows = mapper.selectTopCustomers(FROM, TO);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getCustomerId()).isEqualTo(2L);
        assertThat(rows.get(0).getCustomerName()).as("企业客户取企业名称")
                .isEqualTo("森特供应链有限公司");
        assertThat(rows.get(0).getAmountCent()).isEqualTo(20_000L);
        assertThat(rows.get(0).getOrderCount()).isEqualTo(1);

        assertThat(rows.get(1).getCustomerId()).isEqualTo(1L);
        assertThat(rows.get(1).getCustomerName()).isEqualTo("散客小张");
        assertThat(rows.get(1).getAmountCent()).isEqualTo(15_000L);
        assertThat(rows.get(1).getOrderCount()).isEqualTo(2);

        assertThat(rows.get(2).getCustomerId()).isEqualTo(3L);
        assertThat(rows.get(2).getCustomerName()).as("无客户档案时回落客户#id")
                .isEqualTo("客户#3");
    }

    @Test
    @DisplayName("数据范围：SELF 只看自己订单带来的客户 / TEAM 含下属 / TEAM 空一行不出 / ALL 全量")
    void dataScopeFiltersTopCustomers() {
        insertCustomer(1L, "甲客户", "13800000001", "PERSON");
        insertCustomer(2L, "乙客户", "13800000002", "PERSON");
        insertOrder("SO-TC-G", 1L, 7L, "PAID", LocalDateTime.of(2026, 11, 11, 9, 0), 10_000L);
        insertOrder("SO-TC-H", 2L, 8L, "PAID", LocalDateTime.of(2026, 11, 11, 10, 0), 20_000L);

        DataScopeContext.set(DataScopeContext.Scope.self(7L));
        assertThat(mapper.selectTopCustomers(FROM, TO))
                .extracting(TopCustomerRow::getCustomerId).containsExactly(1L);

        DataScopeContext.set(DataScopeContext.Scope.team(7L, List.of(7L, 8L)));
        assertThat(mapper.selectTopCustomers(FROM, TO))
                .extracting(TopCustomerRow::getCustomerId).containsExactly(2L, 1L);

        DataScopeContext.set(DataScopeContext.Scope.team(7L, List.of()));
        assertThat(mapper.selectTopCustomers(FROM, TO)).as("TEAM 无下属时拒绝全表").isEmpty();

        DataScopeContext.set(DataScopeContext.Scope.all(9001L));
        assertThat(mapper.selectTopCustomers(FROM, TO)).hasSize(2);
    }

    private void insertCustomer(long id, String nickname, String phone, String type) {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, nickname, type, status, token_version, deleted) "
                + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0, 0)",
                id, "C" + id, phone, nickname, type);
    }

    private void insertOrder(String orderNo, long customerId, long ownerStaffId, String payStatus,
                             LocalDateTime paidAt, long paidCent) {
        jdbc.update("INSERT INTO orders (order_no, customer_id, owner_staff_id, status, pay_status, "
                        + "goods_amount_cent, payable_cent, paid_cent, paid_at, idem_key) "
                        + "VALUES (?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?)",
                orderNo, customerId, ownerStaffId, payStatus, paidCent, paidCent, paidCent, paidAt,
                "IDEM-" + orderNo);
    }
}
