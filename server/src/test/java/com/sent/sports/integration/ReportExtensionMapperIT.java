package com.sent.sports.integration;

import com.sent.sports.common.mybatis.DataScopeContext;
import com.sent.sports.report.mapper.ReportExtensionMapper;
import com.sent.sports.report.mapper.po.CustomerMixRow;
import com.sent.sports.report.mapper.po.ProductDetailRow;
import com.sent.sports.report.mapper.po.RepeatCustomerRow;
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
 * 拓展报表真库校验（2026-11-23）：
 * 1) 三条聚合都只算 PAID / PART_REFUNDED + paid_at 窗口内；
 * 2) {@code @DataScope(alias="o")} 真的让 SELF / TEAM / ALL 看到不同数据；
 * 3) 商品明细带出成本与库存、价格来源；复购结构能识别区间前已有成交的客户。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class ReportExtensionMapperIT extends FullStackIntegrationTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 10, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 12, 0, 0);

    @Autowired
    private ReportExtensionMapper mapper;

    @AfterEach
    void clearScope() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("客户结构：按 customer_type 聚合，NULL 归 UNKNOWN；未支付与窗口外不计")
    void customerMixByType() {
        long e1 = insertOrder("SO-RE-E1", 7L, 10L, "ENTERPRISE", "PAID",
                LocalDateTime.of(2026, 11, 11, 10, 0), 5_000L);
        insertItem(e1, 9101L, 2, 5_000L, "AGREEMENT");
        long p1 = insertOrder("SO-RE-P1", 8L, 11L, "PERSON", "PART_REFUNDED",
                LocalDateTime.of(2026, 11, 11, 11, 0), 3_000L);
        insertItem(p1, 9101L, 1, 3_000L, "RETAIL");
        long u1 = insertOrder("SO-RE-U1", 7L, 12L, null, "PAID",
                LocalDateTime.of(2026, 11, 11, 12, 0), 2_000L);
        insertItem(u1, 9101L, 1, 2_000L, "RETAIL");

        long unpaid = insertOrder("SO-RE-U2", 7L, 10L, "ENTERPRISE", "UNPAID",
                LocalDateTime.of(2026, 11, 11, 13, 0), 99_000L);
        insertItem(unpaid, 9101L, 9, 99_000L, "RETAIL");
        long outWindow = insertOrder("SO-RE-OW", 7L, 10L, "ENTERPRISE", "PAID",
                LocalDateTime.of(2026, 11, 1, 10, 0), 88_000L);
        insertItem(outWindow, 9101L, 8, 88_000L, "RETAIL");

        DataScopeContext.set(DataScopeContext.Scope.all(9001L));
        List<CustomerMixRow> rows = mapper.selectCustomerMix(FROM, TO);

        assertThat(rows).hasSize(3);
        assertThat(rows).filteredOn(r -> "ENTERPRISE".equals(r.getCustomerType()))
                .singleElement().satisfies(r -> {
                    assertThat(r.getAmountCent()).isEqualTo(5_000L);
                    assertThat(r.getOrderCount()).isEqualTo(1);
                    assertThat(r.getCustomerCount()).isEqualTo(1);
                });
        assertThat(rows).filteredOn(r -> r.getCustomerType() == null)
                .singleElement().satisfies(r -> assertThat(r.getAmountCent()).isEqualTo(2_000L));
    }

    @Test
    @DisplayName("客户结构数据范围：SELF 只看自己、TEAM 含下属、TEAM 无下属为空")
    void customerMixDataScope() {
        long mine = insertOrder("SO-RE-S1", 7L, 10L, "ENTERPRISE", "PAID",
                LocalDateTime.of(2026, 11, 11, 10, 0), 5_000L);
        insertItem(mine, 9101L, 1, 5_000L, "RETAIL");
        long other = insertOrder("SO-RE-S2", 8L, 11L, "PERSON", "PAID",
                LocalDateTime.of(2026, 11, 11, 11, 0), 3_000L);
        insertItem(other, 9101L, 1, 3_000L, "RETAIL");

        DataScopeContext.set(DataScopeContext.Scope.self(7L));
        assertThat(mapper.selectCustomerMix(FROM, TO)).hasSize(1);

        DataScopeContext.set(DataScopeContext.Scope.team(7L, List.of(7L, 8L)));
        assertThat(mapper.selectCustomerMix(FROM, TO)).hasSize(2);

        DataScopeContext.set(DataScopeContext.Scope.team(7L, List.of()));
        assertThat(mapper.selectCustomerMix(FROM, TO)).isEmpty();
    }

    @Test
    @DisplayName("商品明细：sku 聚合带出成本/库存/价格来源，全额退款行不计")
    void productDetailsWithCostAndStock() {
        jdbc.update("INSERT INTO product (id, product_no, title, status) VALUES (1, 'P-RE-1', '训练背心', 'ON')");
        jdbc.update("INSERT INTO sku (id, spu_id, sku_no, retail_price_cent, floor_price_cent, "
                + "cost_price_cent, status) VALUES (9101, 1, 'SKU-9101', 3000, 2000, 1000, 'ON')");
        jdbc.update("INSERT INTO sku_stock (sku_id, physical_qty, locked_qty, pending_qty, frozen_qty) "
                + "VALUES (9101, 20, 3, 2, 0)");

        long o1 = insertOrder("SO-RE-D1", 7L, 10L, "PERSON", "PAID",
                LocalDateTime.of(2026, 11, 11, 10, 0), 6_000L);
        insertItem(o1, 9101L, 2, 6_000L, "RETAIL");
        long o2 = insertOrder("SO-RE-D2", 7L, 11L, "PERSON", "PAID",
                LocalDateTime.of(2026, 11, 11, 11, 0), 3_000L);
        insertItem(o2, 9101L, 1, 3_000L, "AGREEMENT");
        long fullRefund = insertOrder("SO-RE-D3", 7L, 12L, "PERSON", "FULL_REFUNDED",
                LocalDateTime.of(2026, 11, 11, 12, 0), 30_000L);
        insertItem(fullRefund, 9101L, 10, 30_000L, "RETAIL");

        DataScopeContext.set(DataScopeContext.Scope.all(9001L));
        List<ProductDetailRow> rows = mapper.selectProductDetails(FROM, TO);

        assertThat(rows).hasSize(1);
        ProductDetailRow row = rows.get(0);
        assertThat(row.getSkuId()).isEqualTo(9101L);
        assertThat(row.getQty()).isEqualTo(3L);
        assertThat(row.getAmountCent()).isEqualTo(9_000L);
        assertThat(row.getCostPriceCent()).isEqualTo(1_000L);
        assertThat(row.getStockQty()).isEqualTo(15L);
        assertThat(row.getPriceSources()).contains("RETAIL").contains("AGREEMENT");
        assertThat(row.getOrderCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("复购结构：区间前已有成交的客户识别为复购，只有区间内首单的为新客")
    void repeatCustomerHistory() {
        // 客户 10：区间前 + 区间内都有单 → 复购
        long old = insertOrder("SO-RE-R0", 7L, 10L, "PERSON", "PAID",
                LocalDateTime.of(2026, 10, 1, 10, 0), 1_000L);
        insertItem(old, 9101L, 1, 1_000L, "RETAIL");
        long r1 = insertOrder("SO-RE-R1", 7L, 10L, "PERSON", "PAID",
                LocalDateTime.of(2026, 11, 11, 10, 0), 5_000L);
        insertItem(r1, 9101L, 1, 5_000L, "RETAIL");
        // 客户 11：只在区间内首单 → 新客
        long r2 = insertOrder("SO-RE-R2", 7L, 11L, "PERSON", "PAID",
                LocalDateTime.of(2026, 11, 11, 11, 0), 3_000L);
        insertItem(r2, 9101L, 1, 3_000L, "RETAIL");
        // 客户 12：只有区间前订单 → 区间内无成交，不应返回
        long r3 = insertOrder("SO-RE-R3", 7L, 12L, "PERSON", "PAID",
                LocalDateTime.of(2026, 10, 2, 10, 0), 2_000L);
        insertItem(r3, 9101L, 1, 2_000L, "RETAIL");

        DataScopeContext.set(DataScopeContext.Scope.all(9001L));
        List<RepeatCustomerRow> rows = mapper.selectRepeatCustomers(FROM, TO);

        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(r -> r.getCustomerId() == 10L)
                .singleElement().satisfies(r -> {
                    assertThat(r.getPeriodOrderCount()).isEqualTo(1);
                    assertThat(r.getPeriodAmountCent()).isEqualTo(5_000L);
                    assertThat(r.getFirstPaidAt()).isBefore(FROM);
                    assertThat(r.getTotalOrderCount())
                            .as("历史总订单数含窗口前，用于复购频次分档").isEqualTo(2);
                });
        assertThat(rows).filteredOn(r -> r.getCustomerId() == 11L)
                .singleElement().satisfies(r -> {
                    assertThat(r.getFirstPaidAt()).isAfterOrEqualTo(FROM);
                    assertThat(r.getTotalOrderCount()).isEqualTo(1);
                });
    }

    private long insertOrder(String orderNo, long ownerStaffId, long customerId, String customerType,
                             String payStatus, LocalDateTime paidAt, long paidCent) {
        jdbc.update("INSERT INTO orders (order_no, customer_id, customer_type, owner_staff_id, status, "
                        + "pay_status, goods_amount_cent, payable_cent, paid_cent, paid_at, idem_key) "
                        + "VALUES (?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?)",
                orderNo, customerId, customerType, ownerStaffId, payStatus,
                paidCent, paidCent, paidCent, paidAt, "IDEM-" + orderNo);
        return jdbc.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
    }

    private void insertItem(long orderId, long skuId, int qty, long amountCent, String priceSource) {
        jdbc.update("INSERT INTO order_item (order_id, sku_id, qty, unit_price_cent, amount_cent, "
                        + "discount_alloc_cent, shipped_qty, price_source) VALUES (?, ?, ?, ?, ?, 0, 0, ?)",
                orderId, skuId, qty, amountCent / qty, amountCent, priceSource);
    }
}
