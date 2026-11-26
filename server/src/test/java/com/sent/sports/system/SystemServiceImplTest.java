package com.sent.sports.system;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.SensitiveWordFilter;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.report.dto.DashboardReportDtos;
import com.sent.sports.report.service.DashboardReportService;
import com.sent.sports.stock.dto.admin.AdminStockDtos;
import com.sent.sports.stock.service.StockQueryService;
import com.sent.sports.system.dto.admin.AdminSystemDtos;
import com.sent.sports.system.dto.admin.LogQuery;
import com.sent.sports.system.dto.admin.StaffQuery;
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
import com.sent.sports.system.service.impl.SystemServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统管理：员工 / 角色权限 / 业务参数 / 操作日志。
 * <p>后三者此前是空桩（员工列表恒空、角色恒空、参数恒空），本测试锁定「读真库 + 写落库」的行为：
 * 分页必须用独立 count、角色权限必须是整组替换、参数读写必须走 upsert。</p>
 */
@ExtendWith(MockitoExtension.class)
class SystemServiceImplTest {

    @Mock
    private OperationLogMapper operationLogMapper;
    @Mock
    private StaffMapper staffMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private SysConfigMapper configMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private StockQueryService stockQueryService;
    @Mock
    private DashboardReportService dashboardReportService;

    /** 真词库（小样本）：员工昵称风控要验证真实匹配，不能拿 mock 糊过去。 */
    private final SensitiveWordFilter sensitiveWordFilter = new SensitiveWordFilter(
            List.of("测试违禁词", "傻X", "加V"));

    private SystemServiceImpl newService() {
        return new SystemServiceImpl(operationLogMapper, staffMapper, roleMapper,
                permissionMapper, configMapper, permissionService, stockQueryService,
                dashboardReportService, sensitiveWordFilter);
    }

    /* ---------------- 操作日志 ---------------- */

    @Test
    @DisplayName("recordLog 把六个要素透传到 sys_operation_log")
    void recordLogPersistsAllFields() {
        newService().recordLog("ORDER", "CLOSE", "ORDER", "SO-1", "客户取消", 9003L);

        verify(operationLogMapper).insert("ORDER", "CLOSE", "ORDER", "SO-1", "客户取消", 9003L);
    }

    @Test
    @DisplayName("listLogs 真查 sys_operation_log 并映射出完整审计视图")
    void listLogsQueriesMapperAndMapsPage() {
        OperationLogRow row = sampleRow();
        stubSelectPage(row);
        when(operationLogMapper.countLogs(any(), any(), any(), any(), any(), any())).thenReturn(1L);

        LogQuery query = new LogQuery();
        query.setPage(1);
        query.setSize(10);
        query.setModule("ORDER");
        query.setAction("CLOSE");
        query.setStaffId(9003L);
        query.setTargetType("ORDER");
        query.setTargetId("SO-1");
        query.setKeyword("取消");

        PageResult<AdminSystemDtos.LogVO> result = newService().listLogs(query);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        AdminSystemDtos.LogVO vo = result.getList().get(0);
        assertEquals(7L, vo.logId());
        assertEquals("张三", vo.operator());
        assertEquals("CLOSE", vo.action());
        assertEquals("ORDER", vo.module());
        assertEquals("ORDER", vo.targetType());
        assertEquals("SO-1", vo.targetId());
        assertEquals("客户取消", vo.reason());
        assertEquals("127.0.0.1", vo.ip());
        assertEquals("trace-1", vo.traceId());
        assertEquals("{\"before\":1}", vo.beforeJson());
        assertEquals("{\"after\":2}", vo.afterJson());
        assertEquals(row.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                vo.timestampMillis());
    }

    @Test
    @DisplayName("listLogs 把空白筛选归一为 null，避免空条件污染 SQL")
    void listLogsNormalizesBlankFilters() {
        stubSelectPage(null);
        when(operationLogMapper.countLogs(any(), any(), any(), any(), any(), any())).thenReturn(0L);

        LogQuery query = new LogQuery();
        query.setModule("  ");
        query.setAction("");
        query.setTargetType(" ");
        query.setTargetId("");
        query.setKeyword("   ");

        PageResult<AdminSystemDtos.LogVO> result = newService().listLogs(query);

        assertEquals(0L, result.getTotal());
        assertEquals(0, result.getList().size());
        verify(operationLogMapper).countLogs(isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("listLogs 按分页参数构造 Page 并保留独立 count 总数")
    void listLogsKeepsIndependentTotal() {
        stubSelectPage(null);
        when(operationLogMapper.countLogs(any(), any(), any(), any(), any(), any())).thenReturn(37L);

        LogQuery query = new LogQuery();
        query.setPage(3);
        query.setSize(5);

        PageResult<AdminSystemDtos.LogVO> result = newService().listLogs(query);

        assertEquals(37L, result.getTotal());
        assertEquals(3L, result.getPage());
        assertEquals(5L, result.getSize());
        assertEquals(8L, result.getPages());
    }

    /* ---------------- 员工 ---------------- */

    @Test
    @DisplayName("listStaff 读 sys_staff 分页并用独立 count，状态派生 enabled")
    void listStaffReadsRealRows() {
        when(staffMapper.selectAdminPage(any(Page.class), any(), any())).thenAnswer(invocation -> {
            Page<StaffAdminRow> page = invocation.getArgument(0);
            page.setRecords(List.of(staffRow(9001L, "ADMIN001", "验收管理员", "13800009001", "SUPER_ADMIN", "ACTIVE"),
                    staffRow(9007L, "WH001", "仓管·李强", "13800009007", "WAREHOUSE", "DISABLED")));
            return page;
        });
        when(staffMapper.countAdmin(any(), any())).thenReturn(6L);

        StaffQuery query = new StaffQuery();
        query.setPage(1);
        query.setSize(20);
        PageResult<AdminSystemDtos.StaffVO> result = newService().listStaff(query);

        assertEquals(6L, result.getTotal());
        assertEquals(2, result.getList().size());
        AdminSystemDtos.StaffVO first = result.getList().get(0);
        assertEquals(9001L, first.staffId());
        assertEquals("ADMIN001", first.username());
        assertEquals("验收管理员", first.name());
        assertEquals("SUPER_ADMIN", first.roleCode());
        assertEquals("13800009001", first.phone());
        assertTrue(first.enabled());
        assertFalse(result.getList().get(1).enabled());
    }

    @Test
    @DisplayName("listStaff 把关键词与状态筛选同源下推到分页和独立 count")
    void listStaffPushesFiltersToBothQueries() {
        StaffQuery query = new StaffQuery();
        query.setKeyword("  13800009001  ");
        query.setEnabled(true);

        newService().listStaff(query);

        verify(staffMapper).selectAdminPage(any(Page.class), eq("13800009001"), eq(Boolean.TRUE));
        verify(staffMapper).countAdmin("13800009001", Boolean.TRUE);

        StaffQuery blankKeyword = new StaffQuery();
        blankKeyword.setKeyword("   ");
        blankKeyword.setEnabled(false);

        newService().listStaff(blankKeyword);

        verify(staffMapper).selectAdminPage(any(Page.class), isNull(), eq(Boolean.FALSE));
        verify(staffMapper).countAdmin(isNull(), eq(Boolean.FALSE));
    }

    @Test
    @DisplayName("createStaff 校验手机号唯一并写入 sys_staff")
    void createStaffRejectsDuplicatePhone() {
        when(staffMapper.selectByPhone("13800009001")).thenReturn(new SysStaffPO());

        BizException error = assertThrows(BizException.class, () -> newService().createStaff(
                new AdminSystemDtos.StaffForm("zhang", "张三", "13800009001", "SALES")));

        assertEquals(40001, error.getErrorCode().getCode());
        verify(staffMapper, never()).insert(any(SysStaffPO.class));
    }

    @Test
    @DisplayName("createStaff 解析角色编码后落库，缺手机号直接拒绝")
    void createStaffResolvesRoleAndInserts() {
        when(staffMapper.selectByStaffNo("zhang")).thenReturn(null);
        when(staffMapper.selectByPhone("13800009999")).thenReturn(null);
        when(roleMapper.selectByCode("FINANCE")).thenReturn(role(5L, "FINANCE", "财务"));
        when(staffMapper.insert(any(SysStaffPO.class))).thenAnswer(invocation -> {
            invocation.<SysStaffPO>getArgument(0).setId(9010L);
            return 1;
        });

        AdminSystemDtos.StaffVO vo = newService().createStaff(
                new AdminSystemDtos.StaffForm("zhang", "张三", "13800009999", "FINANCE"));

        assertEquals(9010L, vo.staffId());
        assertEquals("FINANCE", vo.roleCode());
        assertTrue(vo.enabled());

        BizException error = assertThrows(BizException.class, () -> newService().createStaff(
                new AdminSystemDtos.StaffForm("li", "李四", " ", "SALES")));
        assertEquals(40001, error.getErrorCode().getCode());
    }

    @Test
    @DisplayName("createStaff 拒绝敏感词昵称/登录名（客服名字会直接展示给客户）")
    void createStaffRejectsSensitiveNicknameOrUsername() {
        BizException byName = assertThrows(BizException.class, () -> newService().createStaff(
                new AdminSystemDtos.StaffForm("kefu01", "加V返现客服", "13800008888", "SALES")));
        assertEquals(ErrorCode.SENSITIVE_NICKNAME, byName.getErrorCode());
        assertEquals(400, byName.getErrorCode().getCode());
        assertEquals("昵称/用户名包含违禁词，请修改后重试", byName.resolvedMessage());

        BizException byUsername = assertThrows(BizException.class, () -> newService().createStaff(
                new AdminSystemDtos.StaffForm("测试违禁词", "小王", "13800008888", "SALES")));
        assertEquals(ErrorCode.SENSITIVE_NICKNAME, byUsername.getErrorCode());

        verify(staffMapper, never()).insert(any(SysStaffPO.class));
    }

    @Test
    @DisplayName("deleteStaff 先吊销令牌再逻辑删除，员工不存在抛 404")
    void deleteStaffBumpsTokenThenDeletes() {
        when(staffMapper.selectById(9007L)).thenReturn(new SysStaffPO());

        newService().deleteStaff(9007L);

        verify(staffMapper).bumpTokenVersion(9007L);
        verify(staffMapper).deleteById(9007L);

        when(staffMapper.selectById(12345L)).thenReturn(null);
        BizException error = assertThrows(BizException.class, () -> newService().deleteStaff(12345L));
        assertEquals(40401, error.getErrorCode().getCode());
    }

    /* ---------------- 角色权限矩阵 ---------------- */

    @Test
    @DisplayName("listRoles 每个角色带出真实权限码（不再返回空列表）")
    void listRolesReadsRolesWithPermissions() {
        when(roleMapper.selectAllRoles()).thenReturn(List.of(
                role(2L, "CUSTOMER_SERVICE", "客服"), role(5L, "FINANCE", "财务")));
        when(permissionMapper.selectCodesByRoleId(2L)).thenReturn(List.of("chat:serve", "custom:review"));
        when(permissionMapper.selectCodesByRoleId(5L)).thenReturn(List.of("finance:read"));

        List<AdminSystemDtos.RoleVO> roles = newService().listRoles();

        assertEquals(2, roles.size());
        assertEquals("CUSTOMER_SERVICE", roles.get(0).roleCode());
        assertEquals(List.of("chat:serve", "custom:review"), roles.get(0).permCodes());
    }

    @Test
    @DisplayName("updateRolePerms 整组替换权限：先删后插，并清空权限缓存")
    void updateRolePermsReplacesWholeSet() {
        when(roleMapper.selectByCode("CUSTOMER_SERVICE")).thenReturn(role(2L, "CUSTOMER_SERVICE", "客服"));
        when(permissionMapper.selectPermissionId(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return code.equals("chat:serve") ? 10L : ("refund:audit".equals(code) ? 30L : null);
        });

        AdminSystemDtos.RoleVO vo = newService().updateRolePerms(
                new AdminSystemDtos.RolePermForm("CUSTOMER_SERVICE", List.of(" chat:serve ", "refund:audit", "chat:serve")));

        verify(permissionMapper).deleteRolePerms(2L);
        verify(permissionMapper).ensurePermission("chat:serve");
        verify(permissionMapper).ensurePermission("refund:audit");
        verify(permissionMapper).insertRolePerm(2L, 10L);
        verify(permissionMapper).insertRolePerm(2L, 30L);
        verify(permissionService).evictAll();
        assertEquals(List.of("chat:serve", "refund:audit"), vo.permCodes());
    }

    @Test
    @DisplayName("updateRolePerms 内置超管保存后必须保留通配权限 '*'（否则管理员自我锁死）")
    void updateRolePermsKeepsWildcardForSuperAdmin() {
        when(roleMapper.selectByCode("SUPER_ADMIN")).thenReturn(role(1L, "SUPER_ADMIN", "超级管理员"));
        when(permissionMapper.selectPermissionId(anyString())).thenAnswer(invocation ->
                "*".equals(invocation.getArgument(0)) ? 1L : null);

        AdminSystemDtos.RoleVO vo = newService().updateRolePerms(
                new AdminSystemDtos.RolePermForm("SUPER_ADMIN", List.of("system:staff:manage")));

        verify(permissionMapper).ensurePermission("*");
        verify(permissionMapper).insertRolePerm(1L, 1L);
        assertTrue(vo.permCodes().contains("*"));
    }

    @Test
    @DisplayName("updateRolePerms 角色不存在抛 404，且不触碰权限表")
    void updateRolePermsRejectsUnknownRole() {
        when(roleMapper.selectByCode("NOPE")).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> newService().updateRolePerms(
                new AdminSystemDtos.RolePermForm("NOPE", List.of("order:read"))));

        assertEquals(40401, error.getErrorCode().getCode());
        verify(permissionMapper, never()).deleteRolePerms(anyLong());
    }

    /* ---------------- 业务参数 ---------------- */

    @Test
    @DisplayName("listParams 真读 sys_config（不再恒返回空列表）")
    void listParamsReadsConfigTable() {
        when(configMapper.selectAll()).thenReturn(List.of(
                param("order.pay.timeout.minutes", "30"), param("stock.warning.threshold", "10")));

        List<AdminSystemDtos.ParamVO> params = newService().listParams();

        assertEquals(2, params.size());
        assertEquals("order.pay.timeout.minutes", params.get(0).paramKey());
        assertEquals("30", params.get(0).paramValue());
    }

    @Test
    @DisplayName("upsertParam 走 INSERT ... ON DUPLICATE KEY UPDATE，键值空白归一")
    void upsertParamWritesConfig() {
        AdminSystemDtos.ParamVO vo = newService().upsertParam(
                new AdminSystemDtos.ParamForm("  stock.warning.threshold  ", " 20 "));

        verify(configMapper).upsert("stock.warning.threshold", "20");
        assertEquals("stock.warning.threshold", vo.paramKey());
        assertEquals("20", vo.paramValue());
    }

    /* ---------------- 系统配置中心（配置字典化） ---------------- */

    @Test
    @DisplayName("listConfigs 把键名翻译成中文名/说明/单位/展示值，未知键回落键名而不是报错")
    void listConfigsTranslatesKeyNames() {
        when(configMapper.selectAll()).thenReturn(List.of(
                param("order.pay.timeout.minutes", "45"),
                param("legacy.hand.added.key", "abc")));

        List<AdminSystemDtos.ConfigVO> configs = newService().listConfigs();

        assertEquals(2, configs.size());
        AdminSystemDtos.ConfigVO timeout = configs.get(0);
        assertEquals("order.pay.timeout.minutes", timeout.configKey());
        assertEquals("订单支付超时时间", timeout.configName());
        assertEquals("分钟", timeout.unit());
        assertEquals("45", timeout.configValue(), "原始值要原样下发，弹窗回填靠它");
        assertEquals("45 分钟", timeout.displayValue());
        assertTrue(timeout.description().contains("自动取消"));

        // 手工加进库的键没有字典条目：显示键名、说明空白，但必须能正常列出来
        AdminSystemDtos.ConfigVO legacy = configs.get(1);
        assertEquals("legacy.hand.added.key", legacy.configName());
        assertEquals("abc", legacy.displayValue());
    }

    @Test
    @DisplayName("listConfigs 已知键按业务分组排序，未知键排最后（列表不能按英文键名乱序）")
    void listConfigsSortsKnownKeysFirst() {
        when(configMapper.selectAll()).thenReturn(List.of(
                param("zzz.unknown", "1"),
                param("stock.warning.threshold", "10"),
                param("order.pay.timeout.minutes", "30")));

        List<AdminSystemDtos.ConfigVO> configs = newService().listConfigs();

        assertEquals("order.pay.timeout.minutes", configs.get(0).configKey(), "订单类排在库存类前面");
        assertEquals("stock.warning.threshold", configs.get(1).configKey());
        assertEquals("zzz.unknown", configs.get(2).configKey(), "未知键排最后");
    }

    @Test
    @DisplayName("updateConfigValue 先按字典校验取值类型，再落库并写审计日志")
    void updateConfigValueValidatesAndLogs() {
        when(configMapper.selectAll()).thenReturn(List.of(
                param("order.pay.timeout.minutes", "30")));

        AdminSystemDtos.ConfigVO vo = newService().updateConfigValue(
                "order.pay.timeout.minutes", new AdminSystemDtos.ConfigValueForm(" 60 "), 9001L);

        verify(configMapper).upsert("order.pay.timeout.minutes", "60");
        verify(operationLogMapper).insert(eq("SYSTEM"), eq("CONFIG_UPDATE"), eq("SYS_CONFIG"),
                eq("order.pay.timeout.minutes"), anyString(), eq(9001L));
        assertEquals("60", vo.configValue());
        assertEquals("60 分钟", vo.displayValue());
    }

    @Test
    @DisplayName("updateConfigValue 拒绝非数字/空值，且不写库（配错超时时间会静默改掉全站下单规则）")
    void updateConfigValueRejectsInvalidValue() {
        when(configMapper.selectAll()).thenReturn(List.of(
                param("order.pay.timeout.minutes", "30")));

        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> newService().updateConfigValue("order.pay.timeout.minutes",
                        new AdminSystemDtos.ConfigValueForm("半小时"), 9001L)).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> newService().updateConfigValue("order.pay.timeout.minutes",
                        new AdminSystemDtos.ConfigValueForm("   "), 9001L)).getErrorCode());

        verify(configMapper, never()).upsert(anyString(), anyString());
        verify(operationLogMapper, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateConfigValue 只允许改库里已存在的键，避免拼错键名凭空建出一条无人读取的配置")
    void updateConfigValueRejectsUnknownKey() {
        when(configMapper.selectAll()).thenReturn(List.of(param("order.pay.timeout.minutes", "30")));

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BizException.class,
                () -> newService().updateConfigValue("order.pay.timeout.minute",
                        new AdminSystemDtos.ConfigValueForm("60"), 9001L)).getErrorCode());
        verify(configMapper, never()).upsert(anyString(), anyString());
    }

    /* ---------------- 库存预警摘要（跨模块只经 stock 的 service 门面） ---------------- */

    @Test
    @DisplayName("stockWarningReport 真查预警摘要，不再是 P4 占位值")
    void stockWarningReportReadsRealSummary() {
        when(stockQueryService.warningSummary()).thenReturn(
                new AdminStockDtos.StockWarningSummary(9322L, "尤尼克斯 专业款", 2, 10));

        AdminSystemDtos.StockWarningReportVO vo = newService().stockWarningReport();

        assertEquals(9322L, vo.skuId());
        assertEquals("尤尼克斯 专业款", vo.skuName());
        assertEquals(2, vo.availableQty());
        assertEquals(10, vo.threshold());

        when(stockQueryService.warningSummary()).thenReturn(
                new AdminStockDtos.StockWarningSummary(0L, "暂无预警", 0, 0));
        assertEquals("暂无预警", newService().stockWarningReport().skuName());
    }

    /* ---------------- 夹具 ---------------- */

    private void stubSelectPage(OperationLogRow row) {
        when(operationLogMapper.selectLogPage(any(Page.class), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Page<OperationLogRow> page = invocation.getArgument(0);
                    page.setRecords(row == null ? List.of() : List.of(row));
                    return page;
                });
    }

    private StaffAdminRow staffRow(Long id, String staffNo, String name, String phone,
                                   String roleCode, String status) {
        StaffAdminRow row = new StaffAdminRow();
        row.setStaffId(id);
        row.setStaffNo(staffNo);
        row.setName(name);
        row.setPhone(phone);
        row.setRoleCode(roleCode);
        row.setStatus(status);
        return row;
    }

    private SysRolePO role(Long id, String code, String name) {
        SysRolePO po = new SysRolePO();
        po.setId(id);
        po.setCode(code);
        po.setName(name);
        po.setDataScope("ALL");
        po.setIsBuiltin(0);
        return po;
    }

    private ParamRow param(String key, String value) {
        ParamRow row = new ParamRow();
        row.setParamKey(key);
        row.setParamValue(value);
        return row;
    }

    /* ---------------- 三张报表去桩 ---------------- */

    @Test
    @DisplayName("salesReport 真读今日成交汇总，不再是 (\"占位\", 0, 0)")
    void salesReportReadsRealTodaySummary() {
        when(dashboardReportService.dashboard()).thenReturn(
                new DashboardReportDtos.DashboardSummaryVO(
                        "2026-11-12", 3, 128800L, 12, 2, 1, 3, 7, 39));

        var vo = newService().salesReport();

        assertEquals("2026-11-12", vo.date());
        assertEquals(128800L, vo.amountCent());
        assertEquals(3, vo.orderCount());
    }

    @Test
    @DisplayName("salesRankReport 取真查排行的 TOP1，无成交时才回落空文案")
    void salesRankReportReadsRealTopOne() {
        when(dashboardReportService.salesRank(30)).thenReturn(List.of(
                new DashboardReportDtos.SalesRankVO(1, 9001L, "超级管理员", 40, 88, 998000L),
                new DashboardReportDtos.SalesRankVO(2, 9005L, "销售员张三", 30, 60, 512000L)));

        var vo = newService().salesRankReport();

        assertEquals(1, vo.rank());
        assertEquals("超级管理员", vo.name());
        assertEquals(998000L, vo.amountCent());
    }

    @Test
    @DisplayName("salesRankReport 空排行回落「暂无成交」而非占位")
    void salesRankReportHandlesEmpty() {
        when(dashboardReportService.salesRank(30)).thenReturn(List.of());

        var vo = newService().salesRankReport();

        assertEquals("暂无成交", vo.name());
        assertEquals(0L, vo.amountCent());
    }

    private OperationLogRow sampleRow() {
        OperationLogRow row = new OperationLogRow();
        row.setLogId(7L);
        row.setStaffId(9003L);
        row.setOperator("张三");
        row.setModule("ORDER");
        row.setAction("CLOSE");
        row.setTargetType("ORDER");
        row.setTargetId("SO-1");
        row.setReason("客户取消");
        row.setIp("127.0.0.1");
        row.setTraceId("trace-1");
        row.setBeforeJson("{\"before\":1}");
        row.setAfterJson("{\"after\":2}");
        row.setCreatedAt(LocalDateTime.of(2026, 11, 8, 10, 30));
        return row;
    }
}
