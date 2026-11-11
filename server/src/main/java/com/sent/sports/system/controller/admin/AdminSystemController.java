package com.sent.sports.system.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.system.dto.admin.AdminSystemDtos;
import com.sent.sports.system.dto.admin.LogQuery;
import com.sent.sports.system.service.SystemService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理控制器（P4-55）：员工 / 角色权限 / 参数 / 日志 / 三张报表（plan §8.12）。
 * <p>依赖同模块服务 {@link SystemService}（桩，P5 接入仓储）。写/读端点标 {@code @RequirePerm}
 * （P5 拦截器生效前无害）。所有返回均为本模块中立 DTO，不泄露业务领域类型。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSystemController {

    private final SystemService systemService;

    public AdminSystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/staff")
    @RequirePerm("system:staff:manage")
    public R<PageResult<AdminSystemDtos.StaffVO>> listStaff(@ModelAttribute PageQuery page) {
        return R.ok(systemService.listStaff(page));
    }

    @PostMapping("/staff")
    @RequirePerm("system:staff:manage")
    public R<AdminSystemDtos.StaffVO> createStaff(@Valid @RequestBody AdminSystemDtos.StaffForm form) {
        return R.ok(systemService.createStaff(form));
    }

    @PutMapping("/staff/{id}")
    @RequirePerm("system:staff:manage")
    public R<AdminSystemDtos.StaffVO> updateStaff(@PathVariable Long id,
                                                  @Valid @RequestBody AdminSystemDtos.StaffForm form) {
        return R.ok(systemService.updateStaff(id, form));
    }

    @DeleteMapping("/staff/{id}")
    @RequirePerm("system:staff:manage")
    public R<Void> deleteStaff(@PathVariable Long id) {
        systemService.deleteStaff(id);
        return R.ok();
    }

    @PutMapping("/roles/{id}/permissions")
    @RequirePerm("system:role:manage")
    public R<AdminSystemDtos.RoleVO> updateRolePerms(@PathVariable String id,
                                                    @Valid @RequestBody AdminSystemDtos.RolePermForm form) {
        // 路径 id 即角色编码，表单内 roleCode 以路径为准
        return R.ok(systemService.updateRolePerms(
                new AdminSystemDtos.RolePermForm(id, form.permCodes())));
    }

    @GetMapping("/roles")
    @RequirePerm("system:role:manage")
    public R<List<AdminSystemDtos.RoleVO>> listRoles() {
        return R.ok(systemService.listRoles());
    }

    @GetMapping("/params")
    @RequirePerm("system:param:manage")
    public R<List<AdminSystemDtos.ParamVO>> listParams() {
        return R.ok(systemService.listParams());
    }

    @PutMapping("/params")
    @RequirePerm("system:param:manage")
    public R<AdminSystemDtos.ParamVO> upsertParam(@Valid @RequestBody AdminSystemDtos.ParamForm form) {
        return R.ok(systemService.upsertParam(form));
    }

    @GetMapping("/logs")
    @RequirePerm("system:log:view")
    public R<PageResult<AdminSystemDtos.LogVO>> listLogs(@ModelAttribute LogQuery query) {
        return R.ok(systemService.listLogs(query));
    }

    @GetMapping("/reports/sales")
    @RequirePerm("system:report:view")
    public R<AdminSystemDtos.SalesReportVO> salesReport() {
        return R.ok(systemService.salesReport());
    }

    @GetMapping("/reports/sales-rank")
    @RequirePerm("system:report:view")
    public R<AdminSystemDtos.SalesRankReportVO> salesRankReport() {
        return R.ok(systemService.salesRankReport());
    }

    @GetMapping("/reports/stock-warning")
    @RequirePerm("system:report:view")
    public R<AdminSystemDtos.StockWarningReportVO> stockWarningReport() {
        return R.ok(systemService.stockWarningReport());
    }
}
