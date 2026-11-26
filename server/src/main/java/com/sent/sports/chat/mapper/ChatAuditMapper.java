package com.sent.sports.chat.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.chat.mapper.po.ChatAuditRowPO;
import com.sent.sports.chat.mapper.po.ChatAuditStaffPO;
import com.sent.sports.chat.mapper.po.ChatHistoryRowPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 客服审查 / 历史服务的读侧 SQL（列表与 count 条件严格一致）。 */
@Mapper
public interface ChatAuditMapper {

    /**
     * count 与分页共用的过滤片段，杜绝两边漂移（注解值必须是常量表达式）。
     * <p>2026-11-26：只收「已提交工单」的会话——客服先暂存去接待下一位客户时，
     * 半成品不该出现在主管的审查列表里；未填工单的会话同样不出现。</p>
     */
    String AUDIT_FILTERS = " AND s.audit_deleted = 0 AND n.submit_status = 'SUBMITTED' "
            + "<if test='staffId != null'> AND s.staff_id = #{staffId} </if>"
            + "<if test='from != null'> AND s.ended_at &gt;= #{from} </if>"
            + "<if test='to != null'> AND s.ended_at &lt; #{to} </if>"
            + "<if test='result != null'> AND n.result = #{result} </if>"
            + "<if test='keyword != null'> AND (CAST(s.customer_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%') "
            + "OR CAST(s.id AS CHAR) LIKE CONCAT('%', #{keyword}, '%') "
            + "OR n.summary LIKE CONCAT('%', #{keyword}, '%')) </if>";

    @Select("<script>"
            + "SELECT s.id AS sessionId, s.staff_id AS staffId, st.name AS staffName, "
            + "COALESCE(s.started_at, s.created_at) AS startedAt, "
            + "s.ended_at AS endedAt, s.close_reason AS closeReason, "
            + "n.result AS result, "
            + "(SELECT COUNT(*) FROM chat_message m WHERE m.session_id = s.id) AS messageCount "
            + "FROM chat_session s LEFT JOIN sys_staff st ON st.id = s.staff_id "
            + "LEFT JOIN chat_session_note n ON n.session_id = s.id "
            + "WHERE s.customer_id = #{customerId} AND s.status = 'CLOSED' "
            + "<if test='excludeSessionId > 0'> AND s.id &lt;&gt; #{excludeSessionId} </if>"
            + "ORDER BY s.ended_at DESC, s.id DESC LIMIT #{limit}"
            + "</script>")
    List<ChatHistoryRowPO> selectHistory(@Param("customerId") long customerId,
                                         @Param("excludeSessionId") long excludeSessionId,
                                         @Param("limit") int limit);

    @Select("<script>"
            + "SELECT s.id AS sessionId, s.customer_id AS customerId, s.staff_id AS staffId, "
            + "st.name AS staffName, COALESCE(s.started_at, s.created_at) AS startedAt, "
            + "s.ended_at AS endedAt, "
            + "n.summary AS summary, n.result AS result, n.submit_status AS submitStatus "
            + "FROM chat_session s LEFT JOIN sys_staff st ON st.id = s.staff_id "
            + "LEFT JOIN chat_session_note n ON n.session_id = s.id "
            + "WHERE s.status = 'CLOSED' "
            + AUDIT_FILTERS
            + "ORDER BY s.ended_at DESC, s.id DESC"
            + "</script>")
    IPage<ChatAuditRowPO> selectAuditPage(Page<?> page,
                                          @Param("staffId") Long staffId,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to,
                                          @Param("result") String result,
                                          @Param("keyword") String keyword);

    @Select("<script>"
            + "SELECT COUNT(*) FROM chat_session s "
            + "LEFT JOIN chat_session_note n ON n.session_id = s.id "
            + "WHERE s.status = 'CLOSED' "
            + AUDIT_FILTERS
            + "</script>")
    long countAudit(@Param("staffId") Long staffId,
                    @Param("from") LocalDateTime from,
                    @Param("to") LocalDateTime to,
                    @Param("result") String result,
                    @Param("keyword") String keyword);

    @Select("SELECT id AS staffId, name AS name FROM sys_staff WHERE deleted = 0 ORDER BY id")
    List<ChatAuditStaffPO> selectStaffOptions();

    /** 管理端审查批量软删：只打 {@code audit_deleted}，会话与聊天记录保持历史原貌。 */
    @Update("<script>UPDATE chat_session SET audit_deleted = 1 WHERE audit_deleted = 0 "
            + "AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int batchMarkAuditDeleted(@Param("ids") List<Long> ids);
}
