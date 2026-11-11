package com.sent.sports.common.util;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务单号生成（plan §4.3）：前缀(SO/RF/CU/PR) + 日期(yyyyMMdd) + 6 位序列。
 * <p><b>序列已持久化</b>（V17 {@code sys_sequence}）：早期实现为进程内 {@link AtomicLong}，
 * 应用重启后从 1 重新计数，与库中已有单号冲突——表现为「新建订单号命中了历史订单」
 * （下单后支付校验金额不一致 → 40001）。现改为每次从 DB 取号，重启后续增。</p>
 * <p>取号用 MySQL 的 {@code LAST_INSERT_ID(expr)} 技巧在同一条连接内完成「自增 + 读回」，
 * 避免 SELECT/UPDATE 两条语句被 Hikari 分配到不同连接而错位。</p>
 * <p>无 {@link JdbcTemplate}（纯单元测试直接 {@code new IdGen()}）时退化为进程内自增，
 * 保持 {@code IdGenTest} 这类切片测试无需数据库。</p>
 */
@Component
public class IdGen {

    /** 统一序列名：单号只需全局唯一，前缀/日期由调用方拼接。 */
    private static final String SEQ_NAME = "BIZ_NO";

    /** 持久化序列不可用时的兜底计数器（单元测试 / 迁移缺失）。 */
    private final AtomicLong memorySequence = new AtomicLong(0);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public IdGen(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    /** 单元测试用：无持久化序列，退化为进程内自增。 */
    public IdGen() {
        this.jdbcTemplate = null;
    }

    public String nextOrderNo() {
        return next("SO");
    }

    public String next(String prefix) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long n = nextSequence();
        return prefix + date + String.format("%06d", n);
    }

    private long nextSequence() {
        if (jdbcTemplate == null) {
            return memorySequence.incrementAndGet();
        }
        Long value = jdbcTemplate.execute((ConnectionCallback<Long>) this::bumpOnSameConnection);
        return value == null || value <= 0 ? memorySequence.incrementAndGet() : value;
    }

    /**
     * 同一连接内完成「自增 + 读回」。UPDATE 影响 0 行（序列行缺失）时先补行再自增，
     * 保证迁移漏跑也不会退化成 NPE。
     */
    private Long bumpOnSameConnection(Connection connection) {
        Long value = bump(connection);
        if (value == null || value <= 0) {
            ensureRow(connection);
            value = bump(connection);
        }
        return value;
    }

    private Long bump(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE sys_sequence SET seq_value = LAST_INSERT_ID(seq_value + 1) WHERE seq_name = ?")) {
            ps.setString(1, SEQ_NAME);
            if (ps.executeUpdate() == 0) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
            return rs.next() ? rs.getLong(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void ensureRow(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sys_sequence (seq_name, seq_value) VALUES (?, 1000) "
                        + "ON DUPLICATE KEY UPDATE seq_value = seq_value")) {
            ps.setString(1, SEQ_NAME);
            ps.executeUpdate();
        } catch (Exception ignored) {
            // 补行失败：交给调用方兜底的进程内序列，避免阻断业务
        }
    }
}
