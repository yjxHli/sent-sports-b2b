package com.sent.sports.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

/**
 * 集成测试基类（P5-23）：真实 Spring 容器 + 真实 MySQL（库 {@code sent_test}），Flyway 启动时自动迁移。
 * <p>约定：
 * <ul>
 *   <li>具体测试类命名 {@code *IT}，由 maven-failsafe-plugin 在 {@code integration-test} 阶段执行，
 *       与 surefire 的单元测试分开统计；</li>
 *   <li>具体测试类需加 {@code @EnabledIf("com.sent.sports.support.ItDatabase#available")}，
 *       使无数据库环境时整类跳过而非失败；</li>
 *   <li>每个测试方法前清空全部业务表（保留 {@code flyway_schema_history}），保证互不污染；</li>
 *   <li>表之间没有物理外键（见 plan §6.2 约定），故清表无需排序，临时关闭外键检查只为兜底。</li>
 * </ul>
 */
@SpringBootTest(classes = PersistenceTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

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

    /** 读取单列单值计数，便于断言「恰好一行」。 */
    protected int countOf(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
        return count == null ? 0 : count;
    }
}
