package com.sent.sports.integration;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.chat.mapper.ChatAuditMapper;
import com.sent.sports.chat.mapper.po.ChatAuditRowPO;
import com.sent.sports.chat.mapper.po.ChatHistoryRowPO;
import com.sent.sports.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客服审查 / 历史服务读侧 SQL 真库校验。
 *
 * <p>重点盯两件事：① 动态 {@code <script>} 必须真的被 MyBatis 解析（否则 {@code <if>} 会原样进 SQL 报语法错）；
 * ② 分页列表与 count 必须同一套过滤条件，否则审查页「共 N 条」和实际行数对不上。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class ChatAuditMapperIT extends BaseIntegrationTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 11, 11, 10, 0);

    @Autowired
    private ChatAuditMapper mapper;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO sys_staff (id, staff_no, username, name, phone, password_hash, role_id) "
                        + "VALUES (9001, 'CS001', 'service', '客服甲', '13900009001', 'hash', 2)");
        jdbc.update("INSERT INTO sys_staff (id, staff_no, username, name, phone, password_hash, role_id) "
                        + "VALUES (9008, 'CHATMG001', 'chat_mgr', '客服经理', '13900009008', 'hash', 7)");

        session(101L, 9002L, 9001L, T1);
        session(102L, 9002L, 9008L, T2);
        session(103L, 9003L, 9001L, T2);
        session(104L, 9004L, 9001L, null); // ACTIVE：不进审查与历史

        note(101L, 9002L, 9001L, "咨询定制足球印字", "INQUIRY", "CLEAR", "DONE");
        note(103L, 9003L, 9001L, "反馈质量", "QUALITY", "CLEAR", "UNRESOLVED");

        message(101L, 1);
        message(101L, 2);
        message(102L, 1);
    }

    private void session(long id, long customerId, Long staffId, LocalDateTime endedAt) {
        jdbc.update("INSERT INTO chat_session (id, customer_id, staff_id, status, started_at, ended_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, customerId, staffId, endedAt == null ? "ACTIVE" : "CLOSED",
                endedAt == null ? T2 : endedAt.minusMinutes(5), endedAt);
    }

    private void note(long sessionId, long customerId, long staffId,
                      String summary, String category, String info, String result) {
        jdbc.update("INSERT INTO chat_session_note "
                        + "(session_id, customer_id, staff_id, summary, category, issue_info, result, solution) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, '已按口径回复')",
                sessionId, customerId, staffId, summary, category, info, result);
    }

    private void message(long sessionId, int seq) {
        jdbc.update("INSERT INTO chat_message (session_id, seq, sender_type, content) "
                        + "VALUES (?, ?, 'CUSTOMER', '在吗')", sessionId, seq);
    }

    @Test
    @DisplayName("历史服务：只返回该客户已结束会话，排除当前会话，消息条数按会话统计")
    void historyFiltersCustomerStatusAndExclusion() {
        List<ChatHistoryRowPO> rows = mapper.selectHistory(9002L, 101L, 10);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSessionId()).isEqualTo(102L);
        assertThat(rows.get(0).getStaffName()).isEqualTo("客服经理");
        assertThat(rows.get(0).getMessageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("审查分页：列表与 count 在处理结果 / 客服 / 关键字 / 时间过滤下始终一致")
    void auditPageAndCountShareSameFilters() {
        assertAudit(null, null, null, "DONE", null, 1);              // 仅已解决的一条
        assertAudit(9008L, null, null, null, null, 1);               // 客服下拉过滤
        assertAudit(null, null, null, null, "质量", 1);              // 关键字命中问题概述
        assertAudit(null, LocalDateTime.of(2026, 11, 11, 0, 0), null, null, null, 2); // 时间下界
        assertAudit(null, null, null, null, null, 3);                // 只算 CLOSED，ACTIVE 不进审查
    }

    @Test
    @DisplayName("审查软删：只从审查列表/count 隐藏，历史服务回放仍可读")
    void auditSoftDeleteOnlyHidesReviewList() {
        assertAudit(null, null, null, null, null, 3);

        assertThat(mapper.batchMarkAuditDeleted(List.of(101L))).isEqualTo(1);
        assertAudit(null, null, null, null, null, 2);

        List<ChatHistoryRowPO> history = mapper.selectHistory(9002L, 0L, 10);
        assertThat(history).extracting(ChatHistoryRowPO::getSessionId).contains(101L, 102L);
    }

    private void assertAudit(Long staffId, LocalDateTime from, LocalDateTime to,
                             String result, String keyword, int expected) {
        Page<ChatAuditRowPO> page = new Page<>(1, 10);
        List<ChatAuditRowPO> rows = mapper.selectAuditPage(page, staffId, from, to, result, keyword)
                .getRecords();
        long total = mapper.countAudit(staffId, from, to, result, keyword);

        assertThat(total).as("count from=" + from + " result=" + result + " keyword=" + keyword).isEqualTo(expected);
        assertThat(rows).as("列表行数 from=" + from + " result=" + result + " keyword=" + keyword)
                .hasSize(expected);
    }
}
