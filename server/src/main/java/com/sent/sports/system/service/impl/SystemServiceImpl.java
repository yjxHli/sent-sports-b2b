package com.sent.sports.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.report.dto.DashboardReportDtos;
import com.sent.sports.report.service.DashboardReportService;
import com.sent.sports.stock.service.StockQueryService;
import com.sent.sports.system.dto.admin.AdminSystemDtos;
import com.sent.sports.system.dto.admin.LogQuery;
import com.sent.sports.system.mapper.OperationLogMapper;
import com.sent.sports.system.mapper.PermissionMapper;
import com.sent.sports.system.mapper.RoleMapper;
import com.sent.sports.system.mapper.StaffMapper;
import com.sent.sports.system.mapper.SysConfigMapper;
import com.sent.sports.system.mapper.po.OperationLogRow;
import com.sent.sports.system.mapper.po.ParamRow;
import com.sent.sports.system.mapper.po.StaffAdminRow;
import com.sent.sports.system.mapper.po.SysRolePO;
import com.sent.sports.system.mapper.po.SysStaffPO;
import com.sent.sports.system.service.PermissionService;
import com.sent.sports.system.service.SystemService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统管理实现。
 * <p>操作日志早已接真库；员工 / 角色权限 / 业务参数此前是 P4 遗留空桩
 * （{@code List.of()} 与空 {@code PageResult}），导致「员工管理」「角色权限矩阵」「业务参数」三页恒为空白。
 * 现全部改为真库读写：员工走 {@code sys_staff}（左联 {@code sys_role} 取角色编码），
 * 角色权限走 {@code sys_role / sys_permission / sys_role_permission} 整组替换，
 * 参数走 {@code sys_config} 的唯一键 upsert。三张报表也已去桩：预警走 stock 门面，
 * 销售汇总 / 排行走 report 门面（{@code DashboardReportService}）真查库。</p>
 */
@Service
public class SystemServiceImpl implements SystemService {

    /** 新建员工的默认状态与初始口令散列（与 V11 种子账号保持一致，登录仍由账号库校验）。 */
    private static final String DEFAULT_STAFF_STATUS = "ACTIVE";
    private static final String DEFAULT_PASSWORD_HASH = "123456";
    /** 新建员工未指定角色时的兜底角色编码。 */
    private static final String DEFAULT_ROLE_CODE = "SALES";
    /** 内置超管角色编码：保存权限矩阵时必须保留通配权限，否则会把管理员自己锁死。 */
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String WILDCARD_PERMISSION = "*";

    private final OperationLogMapper operationLogMapper;
    private final StaffMapper staffMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final SysConfigMapper configMapper;
    private final PermissionService permissionService;
    /** 预警摘要走 stock 模块的 service 门面（ArchUnit 规则 4：跨模块只经对方 service，不直连 mapper）。 */
    private final StockQueryService stockQueryService;
    /** 销售汇总 / 排行走 report 模块的 service 门面，同样不直连对方 mapper。 */
    private final DashboardReportService dashboardReportService;

    public SystemServiceImpl(OperationLogMapper operationLogMapper,
                             StaffMapper staffMapper,
                             RoleMapper roleMapper,
                             PermissionMapper permissionMapper,
                             SysConfigMapper configMapper,
                             PermissionService permissionService,
                             StockQueryService stockQueryService,
                             DashboardReportService dashboardReportService) {
        this.operationLogMapper = operationLogMapper;
        this.staffMapper = staffMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.configMapper = configMapper;
        this.permissionService = permissionService;
        this.stockQueryService = stockQueryService;
        this.dashboardReportService = dashboardReportService;
    }

    @Override
    public void recordLog(String module, String action, String targetType, String targetId,
                          String reason, Long staffId) {
        operationLogMapper.insert(module, action, targetType, targetId, reason, staffId);
    }

    /* ------------------------------------------------------------------ */
    /* 员工                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public AdminSystemDtos.StaffVO createStaff(AdminSystemDtos.StaffForm form) {
        String staffNo = trim(form.username());
        String phone = trim(form.phone());
        if (phone == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号不能为空");
        }
        if (staffMapper.selectByStaffNo(staffNo) != null || staffMapper.selectByUsername(staffNo) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "登录名「" + staffNo + "」已存在");
        }
        if (staffMapper.selectByPhone(phone) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号「" + phone + "」已被占用");
        }
        SysRolePO role = resolveRole(form.roleCode());

        SysStaffPO po = new SysStaffPO();
        po.setStaffNo(staffNo);
        po.setUsername(staffNo);
        po.setName(trim(form.name()));
        po.setPhone(phone);
        po.setPasswordHash(DEFAULT_PASSWORD_HASH);
        po.setRoleId(role.getId());
        po.setStatus(DEFAULT_STAFF_STATUS);
        po.setTokenVersion(0L);
        po.setDeleted(0);
        staffMapper.insert(po);

        return new AdminSystemDtos.StaffVO(po.getId(), staffNo, trim(form.name()), role.getCode(),
                phone, true);
    }

    @Override
    public AdminSystemDtos.StaffVO updateStaff(Long id, AdminSystemDtos.StaffForm form) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "员工 ID 不能为空");
        }
        SysStaffPO existing = staffMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        String phone = trim(form.phone());
        if (phone != null && !phone.equals(existing.getPhone()) && staffMapper.selectByPhone(phone) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号「" + phone + "」已被占用");
        }
        SysRolePO role = resolveRole(form.roleCode());

        String loginName = trim(form.username());
        if (loginName != null && !loginName.equals(existing.getUsername())) {
            SysStaffPO byUsername = staffMapper.selectByUsername(loginName);
            if (byUsername != null && !byUsername.getId().equals(id)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "登录名「" + loginName + "」已存在");
            }
        }
        // updateById 默认跳过 null 字段；表单 username 同时写 username 与 staff_no（UI 口径不变）
        SysStaffPO patch = new SysStaffPO();
        patch.setId(id);
        patch.setUsername(loginName);
        patch.setStaffNo(loginName);
        patch.setName(trim(form.name()));
        patch.setPhone(phone);
        patch.setRoleId(role.getId());
        staffMapper.updateById(patch);

        return new AdminSystemDtos.StaffVO(id, loginName == null ? existing.getStaffNo() : loginName,
                trim(form.name()), role.getCode(), phone == null ? existing.getPhone() : phone, true);
    }

    @Override
    @UseCase
    public void deleteStaff(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "员工 ID 不能为空");
        }
        SysStaffPO existing = staffMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 先吊销令牌再逻辑删除：删完 token_version 查不到，旧令牌就只能靠"员工不存在"失效
        staffMapper.bumpTokenVersion(id);
        staffMapper.deleteById(id);
    }

    @Override
    public PageResult<AdminSystemDtos.StaffVO> listStaff(PageQuery page) {
        Page<StaffAdminRow> dbPage = new Page<>(page.getPage(), page.getSize());
        staffMapper.selectAdminPage(dbPage);
        long total = staffMapper.countAdmin();
        dbPage.setTotal(total);
        List<AdminSystemDtos.StaffVO> list = dbPage.getRecords().stream()
                .map(row -> new AdminSystemDtos.StaffVO(
                        row.getStaffId(),
                        row.getStaffNo(),
                        row.getName(),
                        row.getRoleCode() == null ? "" : row.getRoleCode(),
                        row.getPhone(),
                        DEFAULT_STAFF_STATUS.equals(row.getStatus())))
                .toList();
        return PageResult.of(list, total, page.getPage(), page.getSize());
    }

    /** 角色编码 → 角色行；空白回退 {@link #DEFAULT_ROLE_CODE}，编码不存在按参数错误处理。 */
    private SysRolePO resolveRole(String roleCode) {
        String code = trim(roleCode);
        SysRolePO role = roleMapper.selectByCode(code == null ? DEFAULT_ROLE_CODE : code);
        if (role == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色编码不存在：" + code);
        }
        return role;
    }

    /* ------------------------------------------------------------------ */
    /* 角色权限矩阵                                                        */
    /* ------------------------------------------------------------------ */

    @Override
    @UseCase
    public AdminSystemDtos.RoleVO updateRolePerms(AdminSystemDtos.RolePermForm form) {
        SysRolePO role = roleMapper.selectByCode(trim(form.roleCode()));
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        List<String> codes = normalizeCodes(form.permCodes());

        permissionMapper.deleteRolePerms(role.getId());
        for (String code : codes) {
            linkPermission(role.getId(), code);
        }
        // 内置超管必须保留 '*'：前端权限目录里没有这个通配码，整组替换会把它删掉，
        // 保存后管理员下一次请求就会被 @RequirePerm 拦下（自我锁死）。
        List<String> effective = new ArrayList<>(codes);
        if (SUPER_ADMIN_ROLE.equals(role.getCode())) {
            linkPermission(role.getId(), WILDCARD_PERMISSION);
            if (!effective.contains(WILDCARD_PERMISSION)) {
                effective.add(WILDCARD_PERMISSION);
            }
        }
        // 权限缓存按员工持有 60 秒，改完立刻清空，避免「保存成功但页面仍无权限」
        permissionService.evictAll();

        return new AdminSystemDtos.RoleVO(role.getCode(), role.getName(), effective);
    }

    private void linkPermission(Long roleId, String code) {
        permissionMapper.ensurePermission(code);
        Long permissionId = permissionMapper.selectPermissionId(code);
        if (permissionId != null) {
            permissionMapper.insertRolePerm(roleId, permissionId);
        }
    }

    @Override
    public List<AdminSystemDtos.RoleVO> listRoles() {
        List<SysRolePO> roles = roleMapper.selectAllRoles();
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .map(role -> new AdminSystemDtos.RoleVO(role.getCode(), role.getName(),
                        role.getId() == null ? List.of()
                                : safeCodes(permissionMapper.selectCodesByRoleId(role.getId()))))
                .toList();
    }

    private static List<String> safeCodes(List<String> codes) {
        return codes == null ? List.of() : codes;
    }

    /* ------------------------------------------------------------------ */
    /* 业务参数                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public AdminSystemDtos.ParamVO upsertParam(AdminSystemDtos.ParamForm form) {
        String key = trim(form.paramKey());
        if (key == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数键不能为空");
        }
        String value = form.paramValue() == null ? "" : form.paramValue().trim();
        configMapper.upsert(key, value);
        return new AdminSystemDtos.ParamVO(key, value);
    }

    @Override
    public List<AdminSystemDtos.ParamVO> listParams() {
        List<ParamRow> rows = configMapper.selectAll();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new AdminSystemDtos.ParamVO(row.getParamKey(), row.getParamValue()))
                .toList();
    }

    /* ------------------------------------------------------------------ */
    /* 操作日志                                                            */
    /* ------------------------------------------------------------------ */

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

    /** 权限码归一：去空白、去空串、去重并保持提交顺序。 */
    private static List<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String code : codes) {
            String value = trim(code);
            if (value != null && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 旧单条汇总契约保留，但不再返回占位：今日成交汇总来自 report 模块的真库聚合。
     * 工作台改用 /reports/dashboard 一次取齐六卡，本端点仅服务旧调用方与接口契约测试。
     */
    @Override
    public AdminSystemDtos.SalesReportVO salesReport() {
        DashboardReportDtos.DashboardSummaryVO summary = dashboardReportService.dashboard();
        return new AdminSystemDtos.SalesReportVO(
                summary.date(), summary.todayAmountCent(), (int) summary.todayOrderCount());
    }

    @Override
    public AdminSystemDtos.SalesRankReportVO salesRankReport() {
        List<DashboardReportDtos.SalesRankVO> ranks = dashboardReportService.salesRank(30);
        if (ranks.isEmpty()) {
            return new AdminSystemDtos.SalesRankReportVO(0, "暂无成交", 0L);
        }
        DashboardReportDtos.SalesRankVO top = ranks.get(0);
        return new AdminSystemDtos.SalesRankReportVO(top.rank(), top.name(), top.amountCent());
    }

    /**
     * 库存预警摘要不再返回占位：经 stock 模块门面读「缺口最大」的一条告急 SKU，
     * 工作台指标卡与 /admin/report/stock-warning 摘要共用同一口径。
     */
    @Override
    public AdminSystemDtos.StockWarningReportVO stockWarningReport() {
        var summary = stockQueryService.warningSummary();
        return new AdminSystemDtos.StockWarningReportVO(
                summary.skuId(), summary.skuName(), summary.availableQty(), summary.threshold());
    }
}
