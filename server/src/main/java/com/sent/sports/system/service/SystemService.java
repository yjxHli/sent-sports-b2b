package com.sent.sports.system.service;

import com.sent.sports.common.web.PageResult;
import com.sent.sports.system.dto.admin.AdminSystemDtos;
import com.sent.sports.system.dto.admin.LogQuery;
import com.sent.sports.system.dto.admin.StaffQuery;

import java.util.List;

/**
 * 系统管理服务接口（P4-55 契约）。
 * <p>员工 / 角色权限 / 参数 / 日志 / 三张报表。实体与仓储尚未建设，
 * 当前由 {@code SystemServiceImpl} 桩实现占位；模块只返回中立 DTO，不依赖任何业务模块领域类型
 * （ArchUnit 规则 4 / 模块化边界）。</p>
 */
public interface SystemService {

    /** 新增员工。 */
    AdminSystemDtos.StaffVO createStaff(AdminSystemDtos.StaffForm form);

    /** 更新员工。 */
    AdminSystemDtos.StaffVO updateStaff(Long id, AdminSystemDtos.StaffForm form);

    /** 删除员工。 */
    void deleteStaff(Long id);

    /** 员工分页列表（关键字与状态筛选在 SQL 生效）。 */
    PageResult<AdminSystemDtos.StaffVO> listStaff(StaffQuery query);

    /** 更新角色权限矩阵。 */
    AdminSystemDtos.RoleVO updateRolePerms(AdminSystemDtos.RolePermForm form);

    /** 角色列表。 */
    List<AdminSystemDtos.RoleVO> listRoles();

    /** 新增/更新参数。 */
    AdminSystemDtos.ParamVO upsertParam(AdminSystemDtos.ParamForm form);

    /** 参数列表。 */
    List<AdminSystemDtos.ParamVO> listParams();

    /**
     * 系统配置中心列表（2026-11-25）：在键值对之上补中文名 / 说明 / 单位 / 展示值。
     * <p>只读侧做翻译，{@code sys_config} 结构不变——老的 {@link #listParams()} 与读取方完全不受影响。</p>
     */
    List<AdminSystemDtos.ConfigVO> listConfigs();

    /**
     * 修改一个配置的值：按键查字典校验取值类型，落库后写审计日志。
     *
     * @param configKey 必须已在 {@code sys_config} 中存在（不允许凭空建键）
     * @param staffId   操作人，用于 {@code sys_operation_log} 追溯
     */
    AdminSystemDtos.ConfigVO updateConfigValue(String configKey, AdminSystemDtos.ConfigValueForm form,
                                               Long staffId);

    /** 操作日志分页（真查 sys_operation_log，条件由 LogQuery 归一后下推到 SQL）。 */
    PageResult<AdminSystemDtos.LogVO> listLogs(LogQuery query);

    /**
     * 记录一条操作日志（跨模块契约）：危险操作必须带原因与算子，供审计追溯。
     *
     * @param staffId Web 请求上下文里的操作人；Job / 系统动作可为 null
     */
    void recordLog(String module, String action, String targetType, String targetId,
                   String reason, Long staffId);

    /** 销售趋势报表。 */
    AdminSystemDtos.SalesReportVO salesReport();

    /** 销售业绩排行报表。 */
    AdminSystemDtos.SalesRankReportVO salesRankReport();

    /** 库存预警报表。 */
    AdminSystemDtos.StockWarningReportVO stockWarningReport();
}
