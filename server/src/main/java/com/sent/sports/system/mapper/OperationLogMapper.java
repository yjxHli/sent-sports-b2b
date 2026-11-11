package com.sent.sports.system.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.system.mapper.po.OperationLogRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 操作日志 Mapper：sys_operation_log 只增不改（审计表）。
 * <p>由各业务模块通过 {@code SystemService.recordLog} 写入，避免跨模块直接依赖本 Mapper
 * （ArchUnit 规则 4：跨模块只走对方 service 接口）。</p>
 */
@Mapper
public interface OperationLogMapper {

    /**
     * 写入一条操作日志。staff_id / created_by 允许为空（Job 或系统自动动作）；
     * 时间统一由数据库时钟生成，避免应用服务器时钟漂移。
     */
    @Insert("INSERT INTO sys_operation_log "
            + "(staff_id, module, action, target_type, target_id, reason, created_at, updated_at, created_by) "
            + "VALUES (#{staffId}, #{module}, #{action}, #{targetType}, #{targetId}, #{reason}, "
            + "NOW(3), NOW(3), #{staffId})")
    int insert(@Param("module") String module,
               @Param("action") String action,
               @Param("targetType") String targetType,
               @Param("targetId") String targetId,
               @Param("reason") String reason,
               @Param("staffId") Long staffId);

    /**
     * 操作日志分页（只读审计）：左联 sys_staff 取操作人姓名。
     * <p>筛选条件一律 {@code (#{x} IS NULL OR ...)}，与 {@link #countLogs} 保持完全一致；
     * ORDER BY 固定为 created_at/id 倒序，不接受外部 sort，避免 SQL 注入。</p>
     */
    @Select("SELECT l.id AS logId, l.staff_id AS staffId, s.name AS operator, "
            + "l.module AS module, l.action AS action, l.target_type AS targetType, "
            + "l.target_id AS targetId, l.reason AS reason, l.ip AS ip, l.trace_id AS traceId, "
            + "l.before_json AS beforeJson, l.after_json AS afterJson, l.created_at AS createdAt "
            + "FROM sys_operation_log l LEFT JOIN sys_staff s ON s.id = l.staff_id "
            + "WHERE (#{module} IS NULL OR l.module = #{module}) "
            + "AND (#{action} IS NULL OR l.action = #{action}) "
            + "AND (#{staffId} IS NULL OR l.staff_id = #{staffId}) "
            + "AND (#{targetType} IS NULL OR l.target_type = #{targetType}) "
            + "AND (#{targetId} IS NULL OR l.target_id = #{targetId}) "
            + "AND (#{keyword} IS NULL OR s.name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR l.action LIKE CONCAT('%', #{keyword}, '%') "
            + "OR l.target_id LIKE CONCAT('%', #{keyword}, '%') "
            + "OR l.reason LIKE CONCAT('%', #{keyword}, '%')) "
            + "ORDER BY l.created_at DESC, l.id DESC")
    IPage<OperationLogRow> selectLogPage(Page<OperationLogRow> page,
                                         @Param("module") String module,
                                         @Param("action") String action,
                                         @Param("staffId") Long staffId,
                                         @Param("targetType") String targetType,
                                         @Param("targetId") String targetId,
                                         @Param("keyword") String keyword);

    /** 与 selectLogPage 同一过滤条件的独立总数（MyBatis-Plus 分页插件不代跑 count）。 */
    @Select("SELECT COUNT(*) FROM sys_operation_log l LEFT JOIN sys_staff s ON s.id = l.staff_id "
            + "WHERE (#{module} IS NULL OR l.module = #{module}) "
            + "AND (#{action} IS NULL OR l.action = #{action}) "
            + "AND (#{staffId} IS NULL OR l.staff_id = #{staffId}) "
            + "AND (#{targetType} IS NULL OR l.target_type = #{targetType}) "
            + "AND (#{targetId} IS NULL OR l.target_id = #{targetId}) "
            + "AND (#{keyword} IS NULL OR s.name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR l.action LIKE CONCAT('%', #{keyword}, '%') "
            + "OR l.target_id LIKE CONCAT('%', #{keyword}, '%') "
            + "OR l.reason LIKE CONCAT('%', #{keyword}, '%'))")
    long countLogs(@Param("module") String module,
                   @Param("action") String action,
                   @Param("staffId") Long staffId,
                   @Param("targetType") String targetType,
                   @Param("targetId") String targetId,
                   @Param("keyword") String keyword);
}
