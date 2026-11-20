package com.sent.sports.support;

import com.sent.sports.SentSportsApplication;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.OrderLineCmd;
import com.sent.sports.payment.gateway.MockPayGateway;
import com.sent.sports.payment.service.dto.PayCallbackCmd;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * 全链路集成测试基类（P5-48~50）：<b>完整</b> Spring 上下文 + 真实 MySQL。
 * <p>与 {@link BaseIntegrationTest}（持久层切片）的区别：这里加载 {@link SentSportsApplication}，
 * 用例 Bean、AOP 事务切面、MyBatis 插件、拦截器、调度配置全部在场，才能证明「核心链路真的跑得通」。
 * <p>每个用例前清空业务表（保留 flyway 历史），原子任务之间互不污染。
 */
@SpringBootTest(classes = SentSportsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class FullStackIntegrationTest {

    private static final String FLYWAY_TABLE = "flyway_schema_history";

    /** Testcontainers 显式开启时切换到一次性 MySQL；默认不注册任何属性，走本机真库。 */
    @DynamicPropertySource
    static void itDatasource(DynamicPropertyRegistry registry) {
        ItContainers.register(registry);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                        + "AND table_name <> '" + FLYWAY_TABLE + "'", String.class);
        for (String table : tables) {
            jdbc.execute("TRUNCATE TABLE `" + table + "`");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /** 造一个可直接下单的 SKU：零售价 + 库存台账（不建 SPU，表间无物理外键）。 */
    protected void seedSku(long skuId, long retailCent, int physicalQty) {
        jdbc.update("INSERT INTO sku (id, spu_id, sku_no, retail_price_cent, floor_price_cent, "
                        + "cost_price_cent, status) VALUES (?, 1, ?, ?, ?, ?, 'ON')",
                skuId, "SKU-" + skuId, retailCent, retailCent * 8 / 10, retailCent * 5 / 10);
        jdbc.update("INSERT INTO sku_stock (sku_id, physical_qty, locked_qty, pending_qty, frozen_qty, safety_qty) "
                + "VALUES (?, ?, 0, 0, 0, 0)", skuId, physicalQty);
    }

    /** 不打折、不叠加、免运费的下单行（价格 = 零售价 × 数量）。 */
    protected OrderLineCmd line(long skuId, int qty) {
        return new OrderLineCmd(skuId, qty, "PERSON", 100, 100, false, List.of());
    }

    /** 集成用例里的收货地址主键（每次 truncate 后重新播种，值固定便于断言）。 */
    protected static final long ADDRESS_ID = 90001L;

    /** 与 application-test.yml 的 app.pay.mock-secret 保持一致，供用例自签渠道通知。 */
    protected static final String TEST_PAY_SECRET = "test-only-fixture-secret";

    /**
     * 播一条收货地址（下单必填）。
     * <p>{@code orderCmd()} 内部会自动调用，用例不必显式播；用 {@code INSERT IGNORE} 保证
     * 同一用例里多次建单不会因主键冲突炸掉。</p>
     */
    protected long seedAddress(long customerId) {
        jdbc.update("INSERT IGNORE INTO customer_address "
                        + "(id, customer_id, receiver, phone, province, city, district, detail, is_default) "
                        + "VALUES (?, ?, '验收客户', '13800138000', '江苏省', '南京市', '玄武区', '中山路 100 号', 1)",
                ADDRESS_ID, customerId);
        return ADDRESS_ID;
    }

    protected CreateOrderCmd orderCmd(String idemKey, long skuId, int qty) {
        return new CreateOrderCmd(1L, seedAddress(1L), idemKey, 0, Integer.MAX_VALUE, 0,
                List.of(line(skuId, qty)));
    }

    /**
     * 构造一条「渠道已签名」的支付通知（与 P0 后的接口契约一致：回调必须带签名）。
     *
     * <p>密钥固定为 {@code application-test.yml} 里的 {@code app.pay.mock-secret}，
     * 因此自签的通知能被应用内的 {@code PayGateway} 验签通过——这正是「用真网关验真签名」的验收方式。</p>
     */
    protected PayCallbackCmd signedCallback(String txnNo, String orderNo, long amountCent) {
        Map<String, String> notify = new MockPayGateway(TEST_PAY_SECRET, Clock.systemUTC())
                .buildCallback(txnNo, orderNo, amountCent, true);
        return PayCallbackCmd.fromNotify(notify);
    }

    protected long orderCount() {
        return countOf("orders");
    }

    protected int lockedQtyOf(long skuId) {
        Integer value = jdbc.queryForObject("SELECT locked_qty FROM sku_stock WHERE sku_id = ?",
                Integer.class, skuId);
        return value == null ? 0 : value;
    }

    protected int physicalQtyOf(long skuId) {
        Integer value = jdbc.queryForObject("SELECT physical_qty FROM sku_stock WHERE sku_id = ?",
                Integer.class, skuId);
        return value == null ? 0 : value;
    }

    protected int stockFlowCount(long skuId, String bizType) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_flow WHERE sku_id = ? AND biz_type = ?",
                Integer.class, skuId, bizType);
        return value == null ? 0 : value;
    }

    protected String statusOf(String orderNo) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE order_no = ?", String.class, orderNo);
    }

    protected String payStatusOf(String orderNo) {
        return jdbc.queryForObject("SELECT pay_status FROM orders WHERE order_no = ?", String.class, orderNo);
    }

    protected long versionOf(String orderNo) {
        Long value = jdbc.queryForObject("SELECT version FROM orders WHERE order_no = ?", Long.class, orderNo);
        return value == null ? 0L : value;
    }

    protected int countOf(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
        return count == null ? 0 : count;
    }
}
