package com.sent.sports.system.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.system.dto.admin.AdminSystemDtos;
import com.sent.sports.system.dto.admin.LogQuery;

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

    /** 员工分页列表。 */
    PageResult<AdminSystemDtos.StaffVO> listStaff(PageQuery page);

    /** 更新角色权限矩阵。 */
    AdminSystemDtos.RoleVO updateRolePerms(AdminSystemDtos.RolePermForm form);

    /** 角色列表。 */
    List<AdminSystemDtos.RoleVO> listRoles();

    /** 新增/更新参数。 */
    AdminSystemDtos.ParamVO upsertParam(AdminSystemDtos.ParamForm form);

    /** 参数列表。 */
    List<AdminSystemDtos.ParamVO> listParams();

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
