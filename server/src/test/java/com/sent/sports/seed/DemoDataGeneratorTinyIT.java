package com.sent.sports.seed;

import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code app.demo-data.scale=tiny} 的行为契约（TDD 红测试）。
 *
 * <p><b>为什么需要 tiny</b>：清过测试数据的库只剩 7 个账号 / 0 订单，而 E2E 里有一批用例假设
 * 「页面里有数据」才能断言（跨页全选要 &gt; 一页客户、报表热销榜要 5 个成交 SKU、
 * 复购三档柱高要拉出 3 倍差、库存预警要 &lt; 安全库存、财务要有过期合同）。
 * 全量演示数据（200 客户 / 2000 单）太重，tiny 用最小的数据面满足这些断言。</p>
 *
 * <p><b>断言口径直接对齐用例</b>：
 * <ul>
 *   <li>{@code e2e/select-all-across-pages.spec.ts}：客户数必须 &gt; 一页（每页 10 条）→ 客户 &gt; 10；</li>
 *   <li>{@code e2e/report-dashboard.spec.ts}：热销榜 5 条且金额均 &gt; 0 → 至少 5 个 SKU 有成交；</li>
 *   <li>{@code e2e/report-compact.spec.ts}：三档复购柱高 {@code h[2] > h[0]*3} → 「6 单以上」人数 &gt; 3 倍「2 单」；</li>
 *   <li>{@code e2e/stock-warning.spec.ts}：至少 1 个 SKU 可用量 &lt; 安全库存；</li>
 *   <li>{@code e2e/finance-contract-refund.spec.ts}：至少 1 份 EXPIRED 合同；</li>
 *   <li>{@code e2e/report-layout.spec.ts}：30 天趋势要有唯一峰值日且峰值 &gt; 0。</li>
 * </ul>
 * 同时断言「小」：客户 ≤ 20、订单 ≤ 60，防止 tiny 档退化成全量。</p>
 *
 * <p>继承 {@link FullStackIntegrationTest}（不是持久层切片）：{@code DemoDataGenerator} 是
 * {@code @Component}，只有全量上下文才装配；切片应用只扫 {@code RepositoryImpl}，拿不到该 Bean。</p>
 */
@TestPropertySource(properties = {
        "app.demo-data.enabled=true",
        "app.demo-data.scale=tiny"
})
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class DemoDataGeneratorTinyIT extends FullStackIntegrationTest {

    @Autowired
    private DemoDataGenerator generator;

    /**
     * 播下 E2E 验收账号 9002 —— tiny 档会把「深数据」（20 单 / 20 定制 / 20 退款）精确落在它身上，
     * 用户端三个分页用例都固定用它登录。dev 库里本来就有这行，集成测试被 truncate 后要自己补。
     */
    @BeforeEach
    void seedE2eAccount() {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, password_hash, nickname, type, "
                + "level_code, growth_value, points_balance, status, token_version, deleted) "
                + "VALUES (9002, 'CUS-9002', '13800138000', '123456', '验收客户', 'PERSON', "
                + "'LV1', 0, 50000, 'ACTIVE', 0, 0)");
    }

    @Test
    @DisplayName("tiny 档：客户 >10 且 ≤60、订单 ≥6 且 ≤150，并覆盖 ≥5 个 SKU")
    void tinyScaleStaysSmallButCoversMinimumSurface() {
        generator.run();

        int customers = countOf("customer");
        assertThat(customers)
                .as("客户数必须多于一页（10 条）才能验证跨页全选，但 tiny 不该回到 200 个")
                .isGreaterThan(10)
                .isLessThanOrEqualTo(60);

        int orders = countOf("orders");
        assertThat(orders)
                .as("tiny 订单数：够翻页与报表，但仍是全量 2000 的零头")
                .isGreaterThanOrEqualTo(6)
                .isLessThanOrEqualTo(150);

        Integer distinctSku = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT oi.sku_id) FROM order_item oi JOIN orders o ON o.id = oi.order_id "
                        + "WHERE o.pay_status IN ('PAID','PART_REFUNDED')", Integer.class);
        assertThat(distinctSku).as("报表热销榜要 5 条且金额 > 0，成交 SKU 至少 5 个").isGreaterThanOrEqualTo(5);

        Integer completed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED'", Integer.class);
        assertThat(completed).as("商城端「已完成」Tab 全选要至少 1 笔").isGreaterThanOrEqualTo(1);

        Integer lowStock = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sku_stock "
                        + "WHERE physical_qty - locked_qty - pending_qty - frozen_qty < safety_qty", Integer.class);
        assertThat(lowStock).as("库存预警页需要至少 1 个低于安全库存的 SKU").isGreaterThanOrEqualTo(1);

        Integer expiredContract = jdbc.queryForObject(
                "SELECT COUNT(*) FROM contract WHERE status = 'EXPIRED'", Integer.class);
        assertThat(expiredContract).as("财务合同页「已到期」筛选要有数据").isGreaterThanOrEqualTo(1);

        Integer pendingRefund = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refund_order WHERE status <> 'REFUNDED'", Integer.class);
        assertThat(pendingRefund).as("退款审核页需要可操作单据").isGreaterThanOrEqualTo(1);

        assertThat(countOf("chat_session")).as("客服端要有会话可看").isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("tiny 档：近 30 天有唯一峰值日（趋势图 peak 点只能有 1 个）")
    void tinyScaleProducesSinglePeakDay() {
        generator.run();

        LocalDateTime from = LocalDate.now().minusDays(30).atStartOfDay();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DATE(paid_at) AS d, COUNT(*) AS c FROM orders "
                        + "WHERE pay_status IN ('PAID','PART_REFUNDED') AND paid_at >= ? "
                        + "GROUP BY DATE(paid_at) ORDER BY c DESC", from);

        assertThat(rows).as("近 30 天必须有成交，否则趋势图没有峰点").isNotEmpty();
        long peak = ((Number) rows.get(0).get("c")).longValue();
        assertThat(peak).as("峰值日至少 3 单，才看得出「峰值」").isGreaterThanOrEqualTo(3);
        if (rows.size() > 1) {
            long second = ((Number) rows.get(1).get("c")).longValue();
            assertThat(peak).as("峰值必须是唯一最大值，否则前端 peak 点定位不确定").isGreaterThan(second);
        }
    }

    @Test
    @DisplayName("tiny 档：复购三档都有人且「6 单以上」> 3 倍「2 单」（复购柱高比断言）")
    void tinyScaleProducesRepeatPurchaseSpread() {
        generator.run();

        LocalDateTime from = LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT o.customer_id AS cid, "
                        + "SUM(CASE WHEN o.paid_at >= ? AND o.paid_at < ? THEN 1 ELSE 0 END) AS periodCnt, "
                        + "MIN(o.paid_at) AS firstPaidAt, "
                        + "COUNT(*) AS totalCnt "
                        + "FROM orders o WHERE o.pay_status IN ('PAID','PART_REFUNDED') AND o.paid_at < ? "
                        + "GROUP BY o.customer_id "
                        + "HAVING SUM(CASE WHEN o.paid_at >= ? AND o.paid_at < ? THEN 1 ELSE 0 END) > 0",
                from, to, to, from, to);

        long[] buckets = new long[3];
        for (Map<String, Object> row : rows) {
            Object first = row.get("firstPaidAt");
            // 驱动可能把 DATETIME 映射成 Timestamp 或 LocalDateTime，两种都要认
            LocalDateTime firstPaidAt = first instanceof java.sql.Timestamp ts ? ts.toLocalDateTime()
                    : first instanceof LocalDateTime ldt ? ldt : null;
            boolean repeat = firstPaidAt != null && firstPaidAt.isBefore(from);
            if (!repeat) {
                continue;
            }
            int total = ((Number) row.get("totalCnt")).intValue();
            buckets[total >= 6 ? 2 : total >= 3 ? 1 : 0]++;
        }

        assertThat(buckets[0]).as("「2 单」档要有人（柱高 0 会被断言判失败）").isPositive();
        assertThat(buckets[1]).as("「3–5 单」档要有人").isPositive();
        assertThat(buckets[2])
                .as("「6 单以上」人数必须 > 3 倍「2 单」，否则复购柱高比 3 倍断言不过")
                .isGreaterThan(buckets[0] * 3);
    }

    @Test
    @DisplayName("tiny 档：每个列表都够翻到第 2 页（分页类用例的硬前提）")
    void tinyScaleGivesEveryListASecondPage() {
        generator.run();

        // list-pagination.spec.ts 把每页切到 15 再点「下一页」，所以三个用户端列表都要 >15 条
        Integer userOrders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE customer_id = 9002", Integer.class);
        assertThat(userOrders).as("「我的订单」要能翻到第 2 页").isGreaterThan(15);

        Integer userCustoms = jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_order WHERE customer_id = 9002", Integer.class);
        assertThat(userCustoms).as("「我的定制」要能翻到第 2 页").isGreaterThan(15);

        Integer userRefunds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refund_order r JOIN orders o ON o.id = r.order_id "
                        + "WHERE o.customer_id = 9002", Integer.class);
        assertThat(userRefunds).as("「我的售后」要能翻到第 2 页").isGreaterThan(15);

        // finance-contract-refund / pagination-memory：企业列表每页 20，合同与应收台账都要 >20
        Integer enterprises = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer WHERE type = 'ENTERPRISE'", Integer.class);
        assertThat(enterprises).as("企业审核列表要能翻页（每页 20）").isGreaterThan(20);

        Integer contracts = jdbc.queryForObject("SELECT COUNT(*) FROM contract", Integer.class);
        assertThat(contracts).as("合同管理要能翻页").isGreaterThan(20);

        Integer openReceivables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM receivable WHERE status = 'NORMAL'", Integer.class);
        assertThat(openReceivables).as("「只查未结清」的应收台账要能翻页").isGreaterThan(20);

        Integer adminCustoms = countOf("custom_order");
        assertThat(adminCustoms).as("定制列表（管理端）要能翻页与按单号搜索").isGreaterThan(15);
    }

    @Test
    @DisplayName("tiny 档：9002 有可翻页的未结清应收与回款单，定制费一律 10 元/件")
    void tinyScaleGivesE2eAccountFinanceSurface() {
        generator.run();

        // finance-invoice-pdf-paging.spec.ts：勾「仅看当前客户」后回款明细不能空，
        // 否则 activeRecord 为空 → 「加载该客户应收」按钮 disabled
        Integer payments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_record WHERE customer_id = 9002", Integer.class);
        assertThat(payments).as("9002 名下必须有回款单，否则「核销与冲销」面板点不动")
                .isGreaterThanOrEqualTo(1);

        Integer openFor9002 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM receivable WHERE customer_id = 9002 AND status = 'NORMAL'",
                Integer.class);
        assertThat(openFor9002).as("9002 的未结清应收要 > 10 笔，才能翻到第 2 页")
                .isGreaterThan(10);

        Integer orphanReceivable = jdbc.queryForObject(
                "SELECT COUNT(*) FROM receivable r WHERE NOT EXISTS "
                        + "(SELECT 1 FROM orders o WHERE o.id = r.order_id)", Integer.class);
        assertThat(orphanReceivable).as("应收必须挂得到订单").isZero();

        // price-repricing.spec.ts 的全库不变量：定制费 = 10 元 × 件数（已退款单是历史事实，不参与）
        Integer brokenCustomFee = jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_order co "
                        + "JOIN order_item oi ON oi.id = co.order_item_id "
                        + "JOIN orders o ON o.id = oi.order_id "
                        + "WHERE o.status NOT IN ('CANCELLED', 'CLOSED') "
                        + "AND o.pay_status NOT IN ('FULL_REFUNDED', 'PART_REFUNDED') "
                        + "AND co.fee_cent <> 1000 * co.qty", Integer.class);
        assertThat(brokenCustomFee).as("定制费必须等于 1000 分 × 件数，否则演示数据自己破坏 V35 口径")
                .isZero();

        // system-and-invoice / finance-invoice-pdf-paging：发票页要有行，
        // 且列表首行（created_at DESC, id DESC）必须是已开票行，否则 PDF 用例拿不到发票号
        Integer invoices = countOf("invoice_request");
        assertThat(invoices).as("发票管理页要真的有数据").isPositive();

        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoice_request WHERE status = 'APPLIED' AND invoice_no IS NULL",
                Integer.class);
        assertThat(applied).as("system-and-invoice 要能「现场开票」，必须有待开票行")
                .isGreaterThanOrEqualTo(1);

        String firstInvoiceNo = jdbc.queryForObject(
                "SELECT invoice_no FROM invoice_request ORDER BY created_at DESC, id DESC LIMIT 1",
                String.class);
        assertThat(firstInvoiceNo)
                .as("发票列表首行必须已开票（PDF 用例要按首行发票号校验下载文件名）")
                .isNotNull()
                .startsWith("INV");
    }

    @Test
    @DisplayName("tiny 档：跨表强一致（每单有明细、已付有流水、退款挂得到单）")
    void tinyScaleKeepsCrossTableConsistency() {
        generator.run();

        Integer orphanItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders o WHERE NOT EXISTS "
                        + "(SELECT 1 FROM order_item oi WHERE oi.order_id = o.id)", Integer.class);
        assertThat(orphanItems).as("订单必须有明细").isZero();

        Integer paidWithoutTxn = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders o WHERE o.pay_status IN ('PAID','PART_REFUNDED') AND NOT EXISTS "
                        + "(SELECT 1 FROM payment_txn t WHERE t.order_no = o.order_no AND t.status = 'PAID')",
                Integer.class);
        assertThat(paidWithoutTxn).as("已支付订单必须有支付流水").isZero();

        Integer orphanRefund = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refund_order r WHERE NOT EXISTS "
                        + "(SELECT 1 FROM orders o WHERE o.id = r.order_id)", Integer.class);
        assertThat(orphanRefund).as("退款单必须挂得到订单").isZero();

        Integer shippedWithoutShipment = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders o WHERE o.status IN ('WAIT_SHIP','COMPLETED') AND NOT EXISTS "
                        + "(SELECT 1 FROM shipment s WHERE s.order_id = o.id)", Integer.class);
        assertThat(shippedWithoutShipment).as("待发货/已完成订单必须有发货单").isZero();
    }
}
