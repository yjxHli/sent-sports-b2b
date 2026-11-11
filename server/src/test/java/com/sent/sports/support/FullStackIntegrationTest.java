package com.sent.sports.support;

import com.sent.sports.SentSportsApplication;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.OrderLineCmd;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

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

    protected CreateOrderCmd orderCmd(String idemKey, long skuId, int qty) {
        return new CreateOrderCmd(1L, idemKey, 0, Integer.MAX_VALUE, 0, List.of(line(skuId, qty)));
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
