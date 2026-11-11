package com.sent.sports.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.system.dto.admin.AdminSystemDtos;
import com.sent.sports.system.dto.admin.LogQuery;
import com.sent.sports.system.mapper.OperationLogMapper;
import com.sent.sports.system.mapper.po.OperationLogRow;
import com.sent.sports.system.service.SystemService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 系统管理实现（P4-55 占位，逐步收口中）。
 * <p>操作日志已接真库：{@code recordLog} 写 {@code sys_operation_log}，{@code listLogs} 分页读同一张表。
 * 员工/角色/参数与三张报表仍为占位，不依赖任何业务模块领域类型，仅使用本模块中立 DTO。</p>
 */
@Service
public class SystemServiceImpl implements SystemService {

    private final OperationLogMapper operationLogMapper;

    public SystemServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void recordLog(String module, String action, String targetType, String targetId,
                          String reason, Long staffId) {
        operationLogMapper.insert(module, action, targetType, targetId, reason, staffId);
    }

    @Override
    public AdminSystemDtos.StaffVO createStaff(AdminSystemDtos.StaffForm form) {
        return new AdminSystemDtos.StaffVO(0L, form.username(), form.name(), form.roleCode());
    }

    @Override
    public AdminSystemDtos.StaffVO updateStaff(Long id, AdminSystemDtos.StaffForm form) {
        return new AdminSystemDtos.StaffVO(id, form.username(), form.name(), form.roleCode());
    }

    @Override
    public void deleteStaff(Long id) {
        // P5 接入仓储后删除
    }

    @Override
    public PageResult<AdminSystemDtos.StaffVO> listStaff(PageQuery page) {
        return PageResult.of(List.of(), 0L, page.getPage(), page.getSize());
    }

    @Override
    public AdminSystemDtos.RoleVO updateRolePerms(AdminSystemDtos.RolePermForm form) {
        return new AdminSystemDtos.RoleVO(form.roleCode(), "占位角色", form.permCodes());
    }

    @Override
    public List<AdminSystemDtos.RoleVO> listRoles() {
        return List.of();
    }

    @Override
    public AdminSystemDtos.ParamVO upsertParam(AdminSystemDtos.ParamForm form) {
        return new AdminSystemDtos.ParamVO(form.paramKey(), form.paramValue());
    }

    @Override
    public List<AdminSystemDtos.ParamVO> listParams() {
        return List.of();
    }

    @Override
    public PageResult<AdminSystemDtos.LogVO> listLogs(LogQuery query) {
        String module = query.normalizedModule();
        String action = query.normalizedAction();
        Long staffId = query.getStaffId();
        String targetType = query.normalizedTargetType();
        String targetId = query.normalizedTargetId();
        String keyword = query.normalizedKeyword();

        Page<OperationLogRow> page = new Page<>(query.getPage(), query.getSize());
        operationLogMapper.selectLogPage(page, module, action, staffId, targetType, targetId, keyword);
        long total = operationLogMapper.countLogs(module, action, staffId, targetType, targetId, keyword);
        page.setTotal(total);
        List<AdminSystemDtos.LogVO> list = page.getRecords().stream().map(this::toLogVo).toList();
        return PageResult.of(list, total, query.getPage(), query.getSize());
    }

    private AdminSystemDtos.LogVO toLogVo(OperationLogRow row) {
        String operator = row.getOperator();
        if (operator == null || operator.isBlank()) {
            operator = row.getStaffId() == null ? "系统" : "员工#" + row.getStaffId();
        }
        return new AdminSystemDtos.LogVO(
                row.getLogId(), operator, row.getAction(), toMillis(row.getCreatedAt()),
                row.getModule(), row.getTargetType(), row.getTargetId(), row.getReason(),
                row.getIp(), row.getTraceId(), row.getBeforeJson(), row.getAfterJson());
    }

    private long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Override
    public AdminSystemDtos.SalesReportVO salesReport() {
        return new AdminSystemDtos.SalesReportVO("占位", 0L, 0);
    }

    @Override
    public AdminSystemDtos.SalesRankReportVO salesRankReport() {
        return new AdminSystemDtos.SalesRankReportVO(1, "占位", 0L);
    }

    @Override
    public AdminSystemDtos.StockWarningReportVO stockWarningReport() {
        return new AdminSystemDtos.StockWarningReportVO(0L, "占位", 0, 0);
    }
}
