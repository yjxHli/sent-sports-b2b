package com.sent.sports.integration;

import com.sent.sports.common.mybatis.DataScopeContext;
import com.sent.sports.report.mapper.HotProductMapper;
import com.sent.sports.report.mapper.po.HotProductRow;
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
 * 热销商品聚合真库验证（2026-11-21）：
 * 1) 口径：只算 PAID / PART_REFUNDED + paid_at 落在窗口内的订单行；
 * 2) 数据范围：{@code @DataScope} 真的让主管只看团队、财务/超管看全部、TEAM 无下属时一行不出；
 * 3) 商品名三层回落：订单行快照 → product.title → 商品#{skuId}。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class HotProductMapperIT extends FullStackIntegrationTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 10, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 12, 0, 0);

    @Autowired
    private HotProductMapper mapper;

    @AfterEach
    void clearScope() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("按 sku_id 聚合数量与金额，未支付与窗口外订单不计；名称三层回落")
    void aggregatesPaidItemsBySku() {
        jdbc.update("INSERT INTO product (id, product_no, title, status) VALUES (1, 'P-HP-1', '训练背心', 'ON')");
        jdbc.update("INSERT INTO sku (id, spu_id, sku_no, retail_price_cent, floor_price_cent, "
                + "cost_price_cent, status) VALUES (9101, 1, 'SKU-9101', 2000, 1600, 1000, 'ON')");
        jdbc.update("INSERT INTO product (id, product_no, title, status) VALUES (3, 'P-HP-3', '新标题', 'ON')");
        jdbc.update("INSERT INTO sku (id, spu_id, sku_no, retail_price_cent, floor_price_cent, "
                + "cost_price_cent, status) VALUES (9103, 3, 'SKU-9103', 2000, 1600, 1000, 'ON')");

        // 两张有效订单（不同归属），一张未支付，一张在窗口外
        long orderA = insertOrder("SO-HP-A", 7L, "PAID", LocalDateTime.of(2026, 11, 11, 10, 0));
        insertItem(orderA, 9101L, 2, 4_000L, null, "L / 蓝");
        insertItem(orderA, 9103L, 1, 1_000L, "旧标题", "M / 红");

        long orderB = insertOrder("SO-HP-B", 8L, "PART_REFUNDED", LocalDateTime.of(2026, 11, 11, 15, 0));
        insertItem(orderB, 9101L, 3, 6_000L, null, "L / 蓝");

        long unpaid = insertOrder("SO-HP-C", 7L, "UNPAID", LocalDateTime.of(2026, 11, 11, 16, 0));
        insertItem(unpaid, 9101L, 99, 198_000L, null, "L / 蓝");

        long expired = insertOrder("SO-HP-D", 7L, "PAID", LocalDateTime.of(2026, 11, 1, 10, 0));
        insertItem(expired, 9101L, 50, 100_000L, null, "L / 蓝");

        DataScopeContext.set(DataScopeContext.Scope.all(9001L));
        List<HotProductRow> rows = mapper.selectHotProducts(FROM, TO);

        assertThat(rows).hasSize(2);
        HotProductRow first = rows.get(0);
        assertThat(first.getSkuId()).isEqualTo(9101L);
        assertThat(first.getQty()).isEqualTo(5L);
        assertThat(first.getAmountCent()).isEqualTo(10_000L);
        assertThat(first.getOrderCount()).isEqualTo(2);
        assertThat(first.getSkuName()).as("无快照时回落 product.title").isEqualTo("训练背心");

        HotProductRow second = rows.get(1);
        assertThat(second.getSkuId()).isEqualTo(9103L);
        assertThat(second.getSkuName()).as("订单行快照优先于商品现名").isEqualTo("旧标题");
        assertThat(second.getSpec()).isEqualTo("M / 红");
    }

    @Test
    @DisplayName("无 SKU / 无 SPU 的历史行回落「商品#{skuId}」，不因 LEFT JOIN 变空")
    void fallsBackToSkuIdWhenNoProduct() {
        long order = insertOrder("SO-HP-E", 7L, "PAID", LocalDateTime.of(2026, 11, 11, 10, 0));
        insertItem(order, 9527L, 1, 500L, null, null);

        DataScopeContext.set(DataScopeContext.Scope.all(9001L));
        List<HotProductRow> rows = mapper.selectHotProducts(FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSkuName()).isEqualTo("商品#9527");
    }

    @Test
    @DisplayName("数据范围：SELF 只看自己 / TEAM 含下属 / TEAM 无下属一行不出 / ALL 全量")
    void dataScopeFiltersHotProducts() {
        long orderA = insertOrder("SO-HP-F", 7L, "PAID", LocalDateTime.of(2026, 11, 11, 10, 0));
        insertItem(orderA, 9101L, 2, 4_000L, "团队内", null);
        long orderB = insertOrder("SO-HP-G", 8L, "PAID", LocalDateTime.of(2026, 11, 11, 11, 0));
        insertItem(orderB, 9102L, 5, 9_000L, "团队外", null);

        DataScopeContext.set(DataScopeContext.Scope.self(7L));
        assertThat(mapper.selectHotProducts(FROM, TO))
                .extracting(HotProductRow::getSkuId).containsExactly(9101L);

        DataScopeContext.set(DataScopeContext.Scope.team(7L, List.of(7L, 8L)));
        assertThat(mapper.selectHotProducts(FROM, TO))
                .extracting(HotProductRow::getSkuId).containsExactly(9102L, 9101L);

        DataScopeContext.set(DataScopeContext.Scope.team(7L, List.of()));
        assertThat(mapper.selectHotProducts(FROM, TO)).as("TEAM 无下属时拒绝全表").isEmpty();

        DataScopeContext.set(DataScopeContext.Scope.all(9001L));
        assertThat(mapper.selectHotProducts(FROM, TO)).hasSize(2);
    }

    private long insertOrder(String orderNo, long ownerStaffId, String payStatus, LocalDateTime paidAt) {
        jdbc.update("INSERT INTO orders (order_no, customer_id, owner_staff_id, status, pay_status, "
                        + "goods_amount_cent, payable_cent, paid_cent, paid_at, idem_key) "
                        + "VALUES (?, 1, ?, 'COMPLETED', ?, 0, 0, 0, ?, ?)",
                orderNo, ownerStaffId, payStatus, paidAt, "IDEM-" + orderNo);
        return jdbc.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
    }

    private void insertItem(long orderId, long skuId, int qty, long amountCent,
                            String titleSnap, String spec) {
        jdbc.update("INSERT INTO order_item (order_id, sku_id, spu_title_snap, spec_snap, qty, "
                        + "unit_price_cent, amount_cent, discount_alloc_cent, shipped_qty) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0)",
                orderId, skuId, titleSnap, spec, qty, amountCent / qty, amountCent);
    }
}
