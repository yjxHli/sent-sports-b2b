package com.sent.sports.integration;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.chat.mapper.ChatMessageMapper;
import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.po.ChatMessagePO;
import com.sent.sports.chat.mapper.po.ChatSessionPO;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.PaymentRecordMapper;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.PaymentRecordPO;
import com.sent.sports.product.mapper.CatalogMapper;
import com.sent.sports.product.mapper.po.LadderPricePO;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderDetailLineRow;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.payment.mapper.PaymentTxnMapper;
import com.sent.sports.payment.mapper.po.PaymentTxnPO;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.po.ProductPO;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundOrderPO;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.mapper.po.SkuStockPO;
import com.sent.sports.system.mapper.OperationLogMapper;
import com.sent.sports.system.mapper.po.OperationLogRow;
import com.sent.sports.support.BaseIntegrationTest;
import com.sent.sports.support.ItDatabase;
import com.sent.sports.system.mapper.StaffMapper;
import com.sent.sports.system.mapper.po.SysStaffPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mapper 层集成测试（P5-10 ~ P5-18）：在真实 MySQL 上验证三类「只有数据库能证明」的行为。
 * <ol>
 *   <li><b>唯一键真的生效</b>：幂等键 / 流水号 / 客户端消息号重复插入必须抛 {@code DuplicateKeyException}；</li>
 *   <li><b>条件更新真的原子</b>：库存预占、状态迁移、支付落账返回 0 行即代表条件不满足（并发语义的地基）；</li>
 *   <li><b>SQL 真的能跑</b>：递归 CTE、{@code FOR UPDATE}、{@code ON DUPLICATE KEY}、排序索引等语法错误编译期发现不了。</li>
 * </ol>
 * 环境不可用时整类跳过（见 {@link ItDatabase}），不阻塞单元测试门禁。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class MapperIT extends BaseIntegrationTest {

    @Autowired
    private StaffMapper staffMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private CatalogMapper catalogMapper;
    @Autowired
    private StockMapper stockMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private PaymentTxnMapper paymentTxnMapper;
    @Autowired
    private RefundOrderMapper refundOrderMapper;
    @Autowired
    private CustomOrderMapper customOrderMapper;
    @Autowired
    private ReceivableMapper receivableMapper;
    @Autowired
    private ChatSessionMapper chatSessionMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private InvoiceMapper invoiceMapper;
    @Autowired
    private PaymentRecordMapper paymentRecordMapper;
    @Autowired
    private OperationLogMapper operationLogMapper;

    @Test
    @DisplayName("V1~V21：二十一次迁移全部应用，55 张表就位（V21 回填 orders.paid_at 并加聚合索引）")
    void migrationsApplyAllThirteenVersions() {
        Integer versions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertThat(versions).isEqualTo(21);

        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'", Integer.class);
        assertThat(tables).isEqualTo(55);
    }

    @Test
    @DisplayName("P5-10：员工 CRUD、手机号登录查询、数据范围裁剪、令牌版本自增")
    void staffCrudAndDataScope() {
        long rootId = insertStaff("S001", "13800000001", null);
        long leaderId = insertStaff("S002", "13800000002", rootId);
        insertStaff("S003", "13800000003", leaderId);

        assertThat(staffMapper.selectByPhone("13800000002").getName()).isEqualTo("员工S002");
        assertThat(staffMapper.selectByStaffNo("S003").getLeaderId()).isEqualTo(leaderId);

        assertThat(staffMapper.selectByDataScope("ALL", leaderId)).hasSize(3);
        assertThat(staffMapper.selectByDataScope("TEAM", leaderId))
                .extracting(SysStaffPO::getId)
                .containsExactlyInAnyOrder(leaderId, staffMapper.selectByStaffNo("S003").getId());
        assertThat(staffMapper.selectByDataScope("SELF", leaderId)).hasSize(1);
        // 越权兜底：未知范围按 SELF 处理
        assertThat(staffMapper.selectByDataScope("WEIRD", leaderId)).hasSize(1);

        assertThat(staffMapper.selectTokenVersion(rootId)).isZero();
        assertThat(staffMapper.bumpTokenVersion(rootId)).isEqualTo(1);
        assertThat(staffMapper.selectTokenVersion(rootId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("P5-11：商品检索、分类递归、SKU/阶梯价/协议价查询")
    void productSearchAndPricing() {
        jdbc.update("INSERT INTO product_category (id, name, sort) VALUES (10, '球类', 0)");
        jdbc.update("INSERT INTO product_category (id, parent_id, name, sort) VALUES (11, 10, '篮球', 0)");
        jdbc.update("INSERT INTO product_category (id, parent_id, name, sort) VALUES (12, 11, '室内篮球', 0)");

        ProductPO spu = new ProductPO();
        spu.setProductNo("P0001");
        spu.setTitle("专业比赛篮球");
        spu.setCategoryId(12L);
        spu.setStatus("ON_SHELF");
        productMapper.insert(spu);

        // 另一个草稿商品：用于验证「只返回已上架」
        ProductPO draft = new ProductPO();
        draft.setProductNo("P0002");
        draft.setTitle("篮球草稿");
        draft.setCategoryId(12L);
        draft.setStatus("DRAFT");
        productMapper.insert(draft);

        jdbc.update("INSERT INTO sku (spu_id, sku_no, retail_price_cent, floor_price_cent, status) "
                + "VALUES (?, 'SKU0001', 19900, 15000, 'ON')", spu.getId());
        Long skuId = jdbc.queryForObject("SELECT id FROM sku WHERE sku_no = 'SKU0001'", Long.class);

        jdbc.update("INSERT INTO ladder_price (sku_id, min_qty, max_qty, price_cent) VALUES (?, 1, 9, 19900)", skuId);
        jdbc.update("INSERT INTO ladder_price (sku_id, min_qty, max_qty, price_cent) VALUES (?, 10, NULL, 17900)", skuId);
        jdbc.update("INSERT INTO agreement_price (customer_id, discount_rate, allow_stacking) VALUES (88, 90, 1)");

        assertThat(productMapper.searchOnShelf(null, null)).hasSize(1);
        assertThat(productMapper.searchOnShelf(12L, "篮球")).hasSize(1);
        assertThat(productMapper.searchOnShelf(99L, null)).isEmpty();

        assertThat(productMapper.selectCategoryAndDescendants(10)).containsExactlyInAnyOrder(10L, 11L, 12L);
        assertThat(productMapper.selectSkusBySpu(spu.getId())).hasSize(1);
        assertThat(productMapper.selectSkuById(skuId).getFloorPriceCent()).isEqualTo(15000L);
        assertThat(productMapper.selectSkusByIds(List.of(skuId))).hasSize(1);
        assertThat(productMapper.selectLadderPrices(skuId)).hasSize(2);
        assertThat(productMapper.selectLadderPrices(skuId).get(1).getPriceCent()).isEqualTo(17900L);
        assertThat(productMapper.selectEffectiveAgreement(88L).getDiscountRate()).isEqualTo(90);
        assertThat(productMapper.selectEffectiveAgreement(89L)).isNull();
    }

    @Test
    @DisplayName("P5-12：库存预占为条件更新——可用不足时返回 0 行（并发抢单的地基）")
    void stockLockIsGuardedByCondition() {
        assertThat(stockMapper.insertIfAbsent(5001L, 3)).isEqualTo(1);
        stockMapper.insertIfAbsent(5001L, 99); // 幂等：已存在则忽略
        assertThat(stockMapper.selectBySkuId(5001L).getSafetyQty()).isEqualTo(3);

        assertThat(stockMapper.adjustPhysical(5001L, 10)).isEqualTo(1);
        assertThat(stockMapper.lockStock(5001L, 6)).isEqualTo(1);
        assertThat(stockMapper.lockStock(5001L, 5)).isZero(); // 可用只剩 4
        assertThat(stockMapper.selectBySkuId(5001L).getLockedQty()).isEqualTo(6);

        assertThat(stockMapper.releaseStock(5001L, 6)).isEqualTo(1);
        assertThat(stockMapper.selectBySkuId(5001L).getLockedQty()).isZero();
        // 不会把 physical 调成负数
        assertThat(stockMapper.adjustPhysical(5001L, -100)).isZero();

        jdbc.update("UPDATE sku_stock SET safety_qty = 20 WHERE sku_id = 5001");
        List<SkuStockPO> warning = stockMapper.selectBelowSafety();
        assertThat(warning).extracting(SkuStockPO::getSkuId).containsExactly(5001L);
    }

    @Test
    @DisplayName("P5-13：订单幂等键唯一 + 状态条件迁移 + 支付落账只生效一次")
    void orderIdempotencyAndConditionalTransit() {
        OrderPO order = new OrderPO();
        order.setOrderNo("O20240001");
        order.setCustomerId(1L);
        order.setIdemKey("idem-001");
        order.setStatus("WAIT_PAY");
        order.setPayStatus("UNPAID");
        assertThat(orderMapper.insert(order)).isEqualTo(1);

        assertThat(orderMapper.selectByIdemKey("idem-001").getOrderNo()).isEqualTo("O20240001");
        assertThat(orderMapper.selectByOrderNo("O20240001").getPayableCent()).isZero();

        OrderPO duplicate = new OrderPO();
        duplicate.setOrderNo("O20240002");
        duplicate.setCustomerId(1L);
        duplicate.setIdemKey("idem-001");
        duplicate.setStatus("WAIT_PAY");
        duplicate.setPayStatus("UNPAID");
        assertThatThrownBy(() -> orderMapper.insert(duplicate)).isInstanceOf(DuplicateKeyException.class);

        assertThat(orderMapper.transitStatus("O20240001", "WAIT_PAY", "WAIT_SHIP")).isEqualTo(1);
        assertThat(orderMapper.transitStatus("O20240001", "WAIT_PAY", "WAIT_SHIP")).isZero();

        assertThat(orderMapper.markPaid("O20240001", 19900L)).isEqualTo(1);
        assertThat(orderMapper.markPaid("O20240001", 19900L)).isZero(); // 重复回调不二次加钱
        assertThat(orderMapper.selectByOrderNo("O20240001").getPaidCent()).isEqualTo(19900L);
        assertThat(orderMapper.selectByOrderNo("O20240001").getPayStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("管理端订单详情行：快照为空时兜底商品名/SKU，定制单状态左联生效")
    void orderDetailLinesFallbackAndCustomStatus() {
        ProductPO spu = new ProductPO();
        spu.setProductNo("P-DETAIL-1");
        spu.setTitle("UV 定制篮球");
        spu.setStatus("ON_SHELF");
        productMapper.insert(spu);
        jdbc.update("INSERT INTO sku (id, spu_id, sku_no, retail_price_cent, floor_price_cent, status) "
                + "VALUES (9102, ?, 'SKU-9102', 20000, 18000, 'ON')", spu.getId());

        OrderPO order = new OrderPO();
        order.setOrderNo("SO-DETAIL-1");
        order.setCustomerId(9002L);
        order.setIdemKey("idem-detail-1");
        order.setStatus("WAIT_SHIP");
        order.setPayStatus("PAID");
        order.setPayableCent(20000L);
        assertThat(orderMapper.insert(order)).isEqualTo(1);
        long orderId = order.getId();

        // 真实下单路径只写 SKU/价格，不写快照；两行分别是「定制行」与「普通行」。
        jdbc.update("INSERT INTO order_item (id, order_id, sku_id, qty, unit_price_cent, amount_cent, "
                + "shipped_qty, custom_flag) VALUES (7001, ?, 9102, 1, 20000, 20000, 0, 1)", orderId);
        jdbc.update("INSERT INTO order_item (id, order_id, sku_id, spu_title_snap, spec_snap, "
                + "qty, unit_price_cent, amount_cent, shipped_qty, custom_flag) "
                + "VALUES (7002, ?, 9102, '下单时快照名', '下单时规格', 2, 20000, 40000, 1, 0)", orderId);
        jdbc.update("INSERT INTO custom_order (custom_no, order_item_id, customer_id, qty, status) "
                + "VALUES ('CUSTOM-DETAIL-1', 7001, 9002, 1, 'APPROVED')");

        List<OrderDetailLineRow> rows = orderMapper.selectDetailLines(orderId);

        assertThat(rows).hasSize(2);
        // 快照为空 → 回退到 product.title / sku.sku_no
        assertThat(rows.get(0).getSkuName()).isEqualTo("UV 定制篮球");
        assertThat(rows.get(0).getSpec()).isEqualTo("SKU-9102");
        assertThat(rows.get(0).getCustomStatus()).isEqualTo("APPROVED");
        // 快照存在 → 以快照为准，不被商品表现值覆盖
        assertThat(rows.get(1).getSkuName()).isEqualTo("下单时快照名");
        assertThat(rows.get(1).getSpec()).isEqualTo("下单时规格");
        assertThat(rows.get(1).getCustomStatus()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("关单真库落账：条件迁移 + refund_order + sys_operation_log 三条 SQL 均可执行")
    void adminCloseSqlRoundTrip() {
        OrderPO order = new OrderPO();
        order.setOrderNo("SO-CLOSE-IT");
        order.setCustomerId(9002L);
        order.setIdemKey("idem-close-it");
        order.setStatus("WAIT_SHIP");
        order.setPayStatus("PAID");
        order.setPayableCent(20000L);
        order.setPaidCent(20000L);
        assertThat(orderMapper.insert(order)).isEqualTo(1);

        // 条件迁移：把状态与支付维度一次性推进到 CLOSED / FULL_REFUNDED
        assertThat(orderMapper.closeOnAdminRefund("SO-CLOSE-IT", "WAIT_SHIP",
                "FULL_REFUNDED", "PAID")).isEqualTo(1);
        // 并发重复关单：旧状态不再匹配，必须返回 0 行
        assertThat(orderMapper.closeOnAdminRefund("SO-CLOSE-IT", "WAIT_SHIP",
                "FULL_REFUNDED", "PAID")).isZero();
        OrderPO after = orderMapper.selectByOrderNo("SO-CLOSE-IT");
        assertThat(after.getStatus()).isEqualTo("CLOSED");
        assertThat(after.getPayStatus()).isEqualTo("FULL_REFUNDED");

        assertThat(refundOrderMapper.insertCloseRefund("RF-CLOSE-IT", order.getId(),
                20000L, "客户取消")).isEqualTo(1);
        RefundOrderPO refund = refundOrderMapper.selectByRefundNo("RF-CLOSE-IT");
        assertThat(refund.getStatus()).isEqualTo("REFUNDED");
        assertThat(refund.getReasonCode()).isEqualTo("ADMIN_CLOSE");
        assertThat(refund.getAmountCent()).isEqualTo(20000L);
        assertThat(refund.getRemark()).isEqualTo("客户取消");

        assertThat(operationLogMapper.insert("ORDER", "CLOSE", "ORDER", "SO-CLOSE-IT",
                "客户取消", 9003L)).isEqualTo(1);
        assertThat(countOf("sys_operation_log")).isEqualTo(1);
    }

    @Test
    @DisplayName("发票闭环：按订单号真库创建申请 → 标记 ISSUED → 列表可查到，订单不存在则 0 行")
    void invoiceApplyRoundTrip() {
        OrderPO order = new OrderPO();
        order.setOrderNo("SO-INVOICE-IT");
        order.setCustomerId(9002L);
        order.setIdemKey("idem-invoice-it");
        order.setStatus("COMPLETED");
        order.setPayStatus("PAID");
        order.setPayableCent(25800L);
        order.setPaidCent(25800L);
        assertThat(orderMapper.insert(order)).isEqualTo(1);

        assertThat(invoiceMapper.insertFromOrder("SO-INVOICE-IT", "森特体育用品有限公司",
                "91320000XX", "finance@example.com")).isEqualTo(1);
        Long invoiceId = invoiceMapper.selectLatestIdByOrderNo("SO-INVOICE-IT");
        assertThat(invoiceId).isNotNull();
        assertThat(invoiceMapper.markIssued(invoiceId, "INV-" + invoiceId, "/files/inv-it.pdf"))
                .isEqualTo(1);

        var rows = invoiceMapper.selectInvoicePage(new Page<>(1, 10), "ISSUED");
        assertThat(rows.getRecords()).hasSize(1);
        var row = rows.getRecords().get(0);
        assertThat(row.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(row.getInvoiceNo()).isEqualTo("INV-" + invoiceId);
        assertThat(row.getStatus()).isEqualTo("ISSUED");
        assertThat(row.getAmountCent()).isEqualTo(25800L);
        assertThat(row.getPdfUrl()).isEqualTo("/files/inv-it.pdf");

        // INSERT...SELECT 找不到订单时返回 0 行，业务侧据此抛 404
        assertThat(invoiceMapper.insertFromOrder("NO-SUCH-ORDER", "抬头", null, null)).isZero();
    }

    @Test
    @DisplayName("管理端订单分页：status 只返回该状态，null 返回全量（发货工作台 409 根因）")
    void adminOrderListFiltersByStatus() {
        insertOrderForList("SO-LIST-WAIT-1", "WAIT_SHIP");
        insertOrderForList("SO-LIST-WAIT-2", "PART_SHIPPED");
        insertOrderForList("SO-LIST-DONE-1", "COMPLETED");

        IPage<OrderPO> waitPage = orderMapper.selectAllPage(new Page<>(1, 10), "WAIT_SHIP");

        assertThat(waitPage.getRecords()).isNotEmpty()
                .allMatch(o -> "WAIT_SHIP".equals(o.getStatus()))
                .extracting(OrderPO::getOrderNo).contains("SO-LIST-WAIT-1");
        assertThat(waitPage.getRecords()).extracting(OrderPO::getOrderNo)
                .doesNotContain("SO-LIST-WAIT-2", "SO-LIST-DONE-1");

        IPage<OrderPO> all = orderMapper.selectAllPage(new Page<>(1, 10), null);
        assertThat(all.getRecords()).extracting(OrderPO::getOrderNo)
                .contains("SO-LIST-WAIT-1", "SO-LIST-WAIT-2", "SO-LIST-DONE-1");
    }

    private void insertOrderForList(String orderNo, String status) {
        OrderPO po = new OrderPO();
        po.setOrderNo(orderNo);
        po.setCustomerId(9002L);
        po.setIdemKey("idem-" + orderNo);
        po.setStatus(status);
        po.setPayStatus("PAID");
        po.setPayableCent(20000L);
        po.setPaidCent(20000L);
        assertThat(orderMapper.insert(po)).isEqualTo(1);
    }

    @Test
    @DisplayName("P5-14：支付流水号唯一，回调落账幂等")
    void paymentTxnUniqueAndCallbackOnce() {
        PaymentTxnPO txn = new PaymentTxnPO();
        txn.setTxnNo("TXN0001");
        txn.setOrderNo("O20240001");
        txn.setAmountCent(19900L);
        txn.setChannel("MOCK");
        txn.setIdemKey("pay-idem-1");
        txn.setStatus("CREATED");
        assertThat(paymentTxnMapper.insert(txn)).isEqualTo(1);

        PaymentTxnPO dup = new PaymentTxnPO();
        dup.setTxnNo("TXN0001");
        dup.setOrderNo("O20240001");
        dup.setAmountCent(19900L);
        dup.setStatus("CREATED");
        assertThatThrownBy(() -> paymentTxnMapper.insert(dup)).isInstanceOf(DuplicateKeyException.class);

        assertThat(paymentTxnMapper.selectByIdemKey("pay-idem-1").getTxnNo()).isEqualTo("TXN0001");
        assertThat(paymentTxnMapper.markCallbackPaid("TXN0001", "CH-1", "{\"ok\":true}")).isEqualTo(1);
        assertThat(paymentTxnMapper.markCallbackPaid("TXN0001", "CH-1", "{\"ok\":true}")).isZero();
        assertThat(paymentTxnMapper.selectByTxnNo("TXN0001").getStatus()).isEqualTo("PAID");
        assertThat(paymentTxnMapper.selectLatestByOrderNo("O20240001").getChannelTradeNo()).isEqualTo("CH-1");
        // 关单全额退款：PAID → REFUNDED 且天然幂等（第二次 0 行）
        assertThat(paymentTxnMapper.markRefunded("TXN0001")).isEqualTo(1);
        assertThat(paymentTxnMapper.markRefunded("TXN0001")).isZero();
        assertThat(paymentTxnMapper.selectByTxnNo("TXN0001").getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("P5-15：退款在途金额合计，失效单不占额度")
    void refundInFlightAmountSumsOnlyActive() {
        insertRefund("R0001", "PENDING", 1000L);
        insertRefund("R0002", "REFUNDED", 2000L);
        insertRefund("R0003", "CLOSED", 5000L);
        insertRefund("R0004", "FAILED", 9000L);

        assertThat(refundOrderMapper.sumInFlightAmount(1L)).isEqualTo(3000L);
        assertThat(refundOrderMapper.selectPending(10)).hasSize(1);
        assertThat(refundOrderMapper.markRefunded("R0001", 7L, "同意")).isEqualTo(1);
        assertThat(refundOrderMapper.markRefunded("R0001", 7L, "同意")).isZero();
        assertThat(refundOrderMapper.sumInFlightAmount(1L)).isEqualTo(3000L);
    }

    @Test
    @DisplayName("P5-16：定制待审队列按截止时间排序，超时扫描只取已过期")
    void customOrderReviewQueueAndTimeout() {
        insertCustom("C0001", "WAIT_REVIEW", LocalDateTime.now().minusHours(30));
        insertCustom("C0002", "WAIT_REVIEW", LocalDateTime.now().plusHours(10));
        insertCustom("C0003", "APPROVED", LocalDateTime.now().minusHours(40));

        assertThat(customOrderMapper.selectPendingReview(10))
                .extracting(CustomOrderPO::getCustomNo)
                .containsExactly("C0001", "C0002");
        assertThat(customOrderMapper.selectReviewTimeout(LocalDateTime.now(), 10))
                .extracting(CustomOrderPO::getCustomNo)
                .containsExactly("C0001");

        assertThat(customOrderMapper.reject("C0002", 9L, "分辨率不足")).isEqualTo(1);
        assertThat(customOrderMapper.selectByCustomNo("C0002").getStatus()).isEqualTo("REJECTED");
        assertThat(customOrderMapper.selectByCustomNo("C0002").getRejectCount()).isEqualTo(1);
        assertThat(customOrderMapper.submitDraft("C0002", LocalDateTime.now().plusDays(1))).isEqualTo(1);
        assertThat(customOrderMapper.selectByCustomNo("C0002").getCurrentVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("P5-17：应收一单一条（重复落账被忽略），核销不得超额，逾期口径正确")
    void receivableWriteOffGuard() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertThat(receivableMapper.insertIfAbsent(1000L, 88L, 10000L, yesterday, "NORMAL")).isEqualTo(1);
        assertThat(receivableMapper.insertIfAbsent(1000L, 88L, 10000L, yesterday, "NORMAL")).isEqualTo(1);
        assertThat(countOf("receivable")).isEqualTo(1);

        var receivable = receivableMapper.selectByOrderId(1000L);
        assertThat(receivableMapper.addReceived(receivable.getId(), 6000L)).isEqualTo(1);
        assertThat(receivableMapper.addReceived(receivable.getId(), 5000L)).isZero(); // 超额被条件挡住
        assertThat(receivableMapper.selectByIdForUpdate(receivable.getId()).getReceivedCent()).isEqualTo(6000L);

        assertThat(receivableMapper.selectOverdue(LocalDate.now())).hasSize(1);
        assertThat(receivableMapper.selectOpenByCustomer(88L)).hasSize(1);
        assertThat(receivableMapper.subtractReceived(receivable.getId(), 6000L)).isEqualTo(1);
        assertThat(receivableMapper.selectByIdForUpdate(receivable.getId()).getReceivedCent()).isZero();
    }

    @Test
    @DisplayName("P5-18：待接入队列会员优先，接入为条件更新，消息序号原子自增")
    void chatQueuePrefersVipAndSeqIsAtomic() {
        long normal = insertSession(0);
        long vip = insertSession(1);

        assertThat(chatSessionMapper.selectWaitingQueue(10))
                .extracting(ChatSessionPO::getId)
                .containsExactly(vip, normal);

        assertThat(chatSessionMapper.assignStaff(vip, 7L)).isEqualTo(1);
        assertThat(chatSessionMapper.assignStaff(vip, 8L)).isZero(); // 已被抢占
        assertThat(chatSessionMapper.countActive()).isEqualTo(1);
        assertThat(chatSessionMapper.selectActiveByStaff(7L)).hasSize(1);

        assertThat(chatSessionMapper.increaseSeq(vip)).isEqualTo(1);
        assertThat(chatSessionMapper.increaseSeq(vip)).isEqualTo(1);
        assertThat(chatSessionMapper.selectSeq(vip)).isEqualTo(2L);

        assertThat(chatSessionMapper.touchLastMsg(vip)).isEqualTo(1);
        assertThat(chatSessionMapper.selectIdleSessions(LocalDateTime.now().plusMinutes(1), 10))
                .extracting(ChatSessionPO::getId)
                .containsExactly(vip);

        assertThat(chatSessionMapper.closeSession(vip, "STAFF_END")).isEqualTo(1);
        assertThat(chatSessionMapper.closeSession(vip, "STAFF_END")).isZero();
        assertThat(chatSessionMapper.countActive()).isZero();
    }

    @Test
    @DisplayName("P5-18：消息按会话+客户端消息号幂等，断线补拉按 seq 增量")
    void chatMessageClientIdIsIdempotent() {
        long sessionId = insertSession(0);
        insertMessage(sessionId, 1L, "c-1");
        insertMessage(sessionId, 2L, "c-2");

        assertThat(chatMessageMapper.selectByClientMsgId(sessionId, "c-2").getSeq()).isEqualTo(2L);

        ChatMessagePO dup = new ChatMessagePO();
        dup.setSessionId(sessionId);
        dup.setSeq(3L);
        dup.setSenderType("CUSTOMER");
        dup.setMsgType("TEXT");
        dup.setContent("重发");
        dup.setClientMsgId("c-2");
        assertThatThrownBy(() -> chatMessageMapper.insert(dup)).isInstanceOf(DuplicateKeyException.class);

        assertThat(chatMessageMapper.selectBySessionId(sessionId)).hasSize(2);
        assertThat(chatMessageMapper.selectSince(sessionId, 1L, 10))
                .extracting(ChatMessagePO::getSeq)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("操作日志分页真查：联查操作人、条件过滤与独立 count 一致")
    void operationLogPageQueriesRealRows() {
        long staffId = insertStaff("LOG-S01", "13900000001", null);
        operationLogMapper.insert("ORDER", "CLOSE", "ORDER", "SO-LOG-1", "客户取消", staffId);
        operationLogMapper.insert("STOCK", "SHIP", "ORDER", "SO-LOG-2", "发货完成", staffId);

        Page<OperationLogRow> all = new Page<>(1, 10);
        IPage<OperationLogRow> rows =
                operationLogMapper.selectLogPage(all, null, null, null, null, null, null);
        assertThat(rows.getRecords()).hasSize(2);
        assertThat(rows.getRecords().get(0).getOperator()).isEqualTo("员工LOG-S01");
        assertThat(operationLogMapper.countLogs(null, null, null, null, null, null)).isEqualTo(2L);

        Page<OperationLogRow> filtered = new Page<>(1, 10);
        operationLogMapper.selectLogPage(filtered, "ORDER", null, null, null, null, "取消");
        assertThat(filtered.getRecords()).hasSize(1);
        assertThat(filtered.getRecords().get(0).getTargetId()).isEqualTo("SO-LOG-1");
        assertThat(filtered.getRecords().get(0).getReason()).isEqualTo("客户取消");
        assertThat(operationLogMapper.countLogs("ORDER", null, null, null, null, "取消")).isEqualTo(1L);

        Page<OperationLogRow> byStaff = new Page<>(1, 10);
        operationLogMapper.selectLogPage(byStaff, null, "SHIP", staffId, null, null, null);
        assertThat(byStaff.getRecords()).hasSize(1);
        assertThat(byStaff.getRecords().get(0).getAction()).isEqualTo("SHIP");
    }

    @Test
    @DisplayName("折扣写侧真落库：阶梯价全量替换、一客一价 upsert/删除，目录读侧带出最低阶梯价")
    void discountWriteThroughReachesCatalog() {
        ProductPO spu = new ProductPO();
        spu.setProductNo("P-IT-DISC");
        spu.setTitle("折扣验收商品");
        spu.setStatus("ON_SHELF");
        productMapper.insert(spu);
        long skuId = 99011L;
        jdbc.update("INSERT INTO sku (id, spu_id, sku_no, spec_json, retail_price_cent, "
                + "floor_price_cent, status) VALUES (?, ?, 'SKU-IT-DISC', '{\"color\":\"red\"}', 10000, 8000, 'ON')",
                skuId, spu.getId());

        jdbc.update("INSERT INTO ladder_price (sku_id, min_qty, max_qty, price_cent) "
                + "VALUES (?, 1, 9, 9900)", skuId);
        assertThat(catalogMapper.selectSkusWithStock(spu.getId()).get(0).getLadderPriceCent())
                .isEqualTo(9900L);

        assertThat(productMapper.deleteLadderPrices(skuId)).isEqualTo(1);
        LadderPricePO first = new LadderPricePO();
        first.setSkuId(skuId);
        first.setMinQty(1);
        first.setMaxQty(9);
        first.setPriceCent(8800L);
        LadderPricePO second = new LadderPricePO();
        second.setSkuId(skuId);
        second.setMinQty(10);
        second.setMaxQty(null);
        second.setPriceCent(8000L);
        assertThat(productMapper.insertLadderPrices(List.of(first, second))).isEqualTo(2);

        assertThat(productMapper.selectLadderPrices(skuId)).hasSize(2);
        assertThat(catalogMapper.selectSkusWithStock(spu.getId()).get(0).getLadderPriceCent())
                .isEqualTo(8000L);

        LocalDateTime from = LocalDateTime.of(2026, 11, 8, 0, 0);
        assertThat(productMapper.upsertCustomerPrice(9002L, skuId, 8500L, from, null)).isPositive();
        Long id = productMapper.selectCustomerPriceId(9002L, skuId);
        assertThat(id).isNotNull();
        assertThat(productMapper.selectCustomerPrices(skuId, 9002L)).hasSize(1);

        assertThat(productMapper.upsertCustomerPrice(9002L, skuId, 8300L, from, null)).isPositive();
        assertThat(productMapper.selectCustomerPrices(skuId, 9002L)).hasSize(1);
        assertThat(productMapper.selectCustomerPrices(skuId, 9002L).get(0).getPriceCent())
                .isEqualTo(8300L);

        assertThat(productMapper.deleteCustomerPrice(id)).isEqualTo(1);
        assertThat(productMapper.selectCustomerPrices(skuId, 9002L)).isEmpty();
    }

    @Test
    @DisplayName("回款列表分页真查：客户 / 状态过滤与独立 count 一致")
    void paymentRecordPageQueriesRealRows() {
        insertPaymentRecord("PR-IT-1", 9002L, 120000L, "PARTIAL");
        insertPaymentRecord("PR-IT-2", 9003L, 30000L, "PENDING");

        Page<PaymentRecordPO> all = new Page<>(1, 10);
        IPage<PaymentRecordPO> rows =
                paymentRecordMapper.selectPaymentRecordPage(all, null, null);
        assertThat(rows.getRecords()).hasSize(2);
        assertThat(paymentRecordMapper.countPaymentRecord(null, null)).isEqualTo(2L);

        Page<PaymentRecordPO> filtered = new Page<>(1, 10);
        paymentRecordMapper.selectPaymentRecordPage(filtered, 9002L, "PARTIAL");
        assertThat(filtered.getRecords()).hasSize(1);
        assertThat(filtered.getRecords().get(0).getRecordNo()).isEqualTo("PR-IT-1");
        assertThat(filtered.getRecords().get(0).getWrittenOffCent()).isEqualTo(20000L);
        assertThat(paymentRecordMapper.countPaymentRecord(9002L, "PARTIAL")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------- 造数辅助

    private void insertPaymentRecord(String recordNo, long customerId, long amountCent, String status) {
        jdbc.update("INSERT INTO payment_record "
                        + "(record_no, customer_id, amount_cent, written_off_cent, bank_serial, status, "
                        + "created_at, updated_at) VALUES (?, ?, ?, 20000, ?, ?, NOW(3), NOW(3))",
                recordNo, customerId, amountCent, "BANK-" + recordNo, status);
    }

    private long insertStaff(String staffNo, String phone, Long leaderId) {
        SysStaffPO staff = new SysStaffPO();
        staff.setStaffNo(staffNo);
        staff.setName("员工" + staffNo);
        staff.setPhone(phone);
        staff.setPasswordHash("{noop}secret");
        staff.setRoleId(1L);
        staff.setLeaderId(leaderId);
        staff.setStatus("ACTIVE");
        staff.setFailCount(0);
        staff.setTokenVersion(0L);
        staffMapper.insert(staff);
        return staff.getId();
    }

    private void insertRefund(String refundNo, String status, long amountCent) {
        RefundOrderPO refund = new RefundOrderPO();
        refund.setRefundNo(refundNo);
        refund.setOrderId(1L);
        refund.setType("ONLY_REFUND");
        refund.setAmountCent(amountCent);
        refund.setStatus(status);
        refundOrderMapper.insert(refund);
    }

    private void insertCustom(String customNo, String status, LocalDateTime deadline) {
        CustomOrderPO custom = new CustomOrderPO();
        custom.setCustomNo(customNo);
        custom.setCustomerId(88L);
        custom.setServiceType("ATTACHED");
        custom.setPrintSide("SINGLE");
        custom.setQty(2);
        custom.setFeeCent(2000L);
        custom.setStatus(status);
        custom.setCurrentVersion(0);
        custom.setRejectCount(0);
        custom.setReviewDeadline(deadline);
        customOrderMapper.insert(custom);
    }

    private long insertSession(int vip) {
        ChatSessionPO session = new ChatSessionPO();
        session.setCustomerId(88L);
        session.setStatus("WAITING");
        session.setSourceType("NONE");
        session.setVip(vip);
        session.setPriority(0);
        session.setSeq(0L);
        chatSessionMapper.insert(session);
        return session.getId();
    }

    private void insertMessage(long sessionId, long seq, String clientMsgId) {
        ChatMessagePO message = new ChatMessagePO();
        message.setSessionId(sessionId);
        message.setSeq(seq);
        message.setSenderType("CUSTOMER");
        message.setMsgType("TEXT");
        message.setContent("消息" + seq);
        message.setClientMsgId(clientMsgId);
        chatMessageMapper.insert(message);
    }
}
