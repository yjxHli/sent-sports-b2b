package com.sent.sports.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;

/**
 * 集成测试的 Testcontainers 数据源（验收阶段）。
 *
 * <p><b>为什么默认关闭：</b>本仓库迁移脚本依赖 MySQL 8 专有的 {@code ENGINE=InnoDB}、{@code JSON} 列、
 * {@code DATETIME(3) ON UPDATE CURRENT_TIMESTAMP(3)}，H2 无法忠实还原；本机已装 MySQL 8.0.43 且
 * {@code sent_test} 可达，但<b>没有 Docker 守护进程</b>（{@code docker: command not found}）。若无条件启动
 * 容器，{@code mvn -o verify} 里的 24 个集成测试会全部失败。因此这里做成显式开关：</p>
 * <pre>
 *   # 有 Docker 的机器：用一次性 MySQL 容器跑全套 IT
 *   mvn -o verify -Dit.testcontainers=true
 *   # 本机 / 无 Docker：回落 application-test.yml 的 127.0.0.1:3306/sent_test
 *   mvn -o verify
 * </pre>
 * <p>开关打开时由两个 IT 基类的 {@code @DynamicPropertySource} 统一注册连接属性，
 * {@link ItDatabase#available()} 直接放行，保证所有 *IT 连的是同一个容器实例（静态单例）。</p>
 */
public final class ItContainers {

    /** 显式开关：{@code -Dit.testcontainers=true}。 */
    public static final String ENABLE_PROPERTY = "it.testcontainers";

    private static volatile MySQLContainer<?> container;

    private ItContainers() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    /** 由测试基类的 {@code @DynamicPropertySource} 调用；未开启时什么都不做，行为与改造前完全一致。 */
    public static synchronized void register(DynamicPropertyRegistry registry) {
        if (!enabled()) {
            return;
        }
        if (container == null) {
            MySQLContainer<?> created = new MySQLContainer<>("mysql:8.0.43")
                    .withDatabaseName("sent_test")
                    .withUsername("root")
                    .withPassword("123456");
            created.start();
            container = created;
        }
        MySQLContainer<?> c = container;
        registry.add("spring.datasource.url", c::getJdbcUrl);
        registry.add("spring.datasource.username", c::getUsername);
        registry.add("spring.datasource.password", c::getPassword);
        registry.add("spring.datasource.driver-class-name", c::getDriverClassName);
    }
}
