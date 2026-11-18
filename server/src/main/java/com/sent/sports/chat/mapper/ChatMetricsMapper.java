package com.sent.sports.chat.mapper;

import com.sent.sports.chat.mapper.po.ChatMetricsRowPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服指标聚合读侧。
 *
 * <p>三路子查询各自聚合后再 LEFT JOIN 到在职客服，避免「一个会话多条消息/评价」
 * 被直接 JOIN 放大计数。区间统一 {@code >= from AND < to}（半开），由服务层保证非空。</p>
 */
@Mapper
public interface ChatMetricsMapper {

    @Select("<script>"
            + "SELECT st.id AS staffId, st.name AS staffName, "
            + "COALESCE(c.closedCount, 0) AS closedCount, "
            + "COALESCE(r.ratedCount, 0) AS ratedCount, "
            + "COALESCE(r.satisfiedCount, 0) AS satisfiedCount, "
            + "COALESCE(n.resolvedCount, 0) AS resolvedCount, "
            + "COALESCE(c.avgDurationSeconds, 0) AS avgDurationSeconds "
            + "FROM sys_staff st "
            + "LEFT JOIN ("
            + "  SELECT staff_id, COUNT(*) AS closedCount, "
            + "  AVG(TIMESTAMPDIFF(SECOND, COALESCE(started_at, created_at), ended_at)) AS avgDurationSeconds "
            + "  FROM chat_session WHERE status = 'CLOSED' AND staff_id IS NOT NULL "
            + "  AND ended_at &gt;= #{from} AND ended_at &lt; #{to} "
            + "  GROUP BY staff_id"
            + ") c ON c.staff_id = st.id "
            + "LEFT JOIN ("
            + "  SELECT staff_id, COUNT(*) AS ratedCount, "
            + "  SUM(CASE WHEN score = 'SATISFIED' THEN 1 ELSE 0 END) AS satisfiedCount "
            + "  FROM chat_satisfaction "
            + "  WHERE created_at &gt;= #{from} AND created_at &lt; #{to} "
            + "  GROUP BY staff_id"
            + ") r ON r.staff_id = st.id "
            + "LEFT JOIN ("
            + "  SELECT s.staff_id, COUNT(*) AS resolvedCount FROM chat_session s "
            + "  JOIN chat_session_note nt ON nt.session_id = s.id "
            + "  WHERE s.status = 'CLOSED' AND s.staff_id IS NOT NULL AND nt.result = 'DONE' "
            + "  AND s.ended_at &gt;= #{from} AND s.ended_at &lt; #{to} "
            + "  GROUP BY s.staff_id"
            + ") n ON n.staff_id = st.id "
            + "WHERE st.deleted = 0 "
            + "<if test='staffId != null'> AND st.id = #{staffId} </if> "
            + "ORDER BY c.closedCount DESC, st.id ASC"
            + "</script>")
    List<ChatMetricsRowPO> selectMetrics(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         @Param("staffId") Long staffId);
}
