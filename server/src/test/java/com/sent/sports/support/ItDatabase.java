package com.sent.sports.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 集成测试数据库探测（P5-23）。
 * <p>为什么需要它：本项目集成测试需要真实 MySQL（迁移脚本含 {@code ENGINE=InnoDB}、{@code JSON} 列、{@code ON UPDATE CURRENT_TIMESTAMP(3)}
 * 等 MySQL 专有语法，H2 兼容模式无法忠实还原）。开发机 / CI 若没有可用实例，测试应「跳过」而不是「失败」，
 * 这样单元测试门禁不会因环境缺失而变红；一旦实例可用，集成测试即真实执行。
 * <p>连接参数走 {@code application-test.yml}，可用环境变量 {@code DB_USERNAME / DB_PASSWORD} 或
 * {@code -Dspring.datasource.url=...} 覆盖（Testcontainers 场景注入的正是后者）。
 */
public final class ItDatabase {

    private static final String HOST = System.getProperty("it.db.host", "127.0.0.1");
    private static final int PORT = Integer.getInteger("it.db.port", 3306);
    private static final int CONNECT_TIMEOUT_MS = 500;

    private static volatile Boolean available;

    private ItDatabase() {
    }

    /** 由 {@code @EnabledIf} 引用：Testcontainers 已接管数据源，或本机 MySQL 端口可达，则执行集成测试。 */
    public static boolean available() {
        if (ItContainers.enabled()) {
            return true;
        }
        Boolean cached = available;
        if (cached == null) {
            cached = probe();
            available = cached;
        }
        return cached;
    }

    private static boolean probe() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
