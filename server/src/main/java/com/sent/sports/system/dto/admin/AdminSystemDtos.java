package com.sent.sports.system.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 系统管理接口 Web 层 DTO（P4-54）。
 * 员工 / 角色权限 / 参数 / 日志 / 三张报表的表单与视图（均为中立 DTO，不依赖业务领域类型）。
 */
public final class AdminSystemDtos {

    private AdminSystemDtos() {
    }

    /** 员工表单。 */
    public record StaffForm(
            @NotBlank(message = "账号不能为空") String username,
            @NotBlank(message = "姓名不能为空") String name,
            String roleCode) {
    }

    /** 角色权限表单。 */
    public record RolePermForm(
            @NotBlank(message = "角色编码不能为空") String roleCode,
            @NotNull(message = "权限清单不能为空") List<String> permCodes) {
    }

    /** 参数表单。 */
    public record ParamForm(
            @NotBlank(message = "参数键不能为空") String paramKey,
            String paramValue) {
    }

    /** 员工视图。 */
    public record StaffVO(Long staffId, String username, String name, String roleCode) {
    }

    /** 角色视图。 */
    public record RoleVO(String roleCode, String roleName, List<String> permCodes) {
    }

    /** 参数视图。 */
    public record ParamVO(String paramKey, String paramValue) {
    }

    /**
     * 日志视图（对齐 sys_operation_log 全字段）。
     * <p>operator 由 staff_id 左联 sys_staff.name 得到；Job / 系统动作无操作人时回退为员工号占位。</p>
     */
    public record LogVO(Long logId, String operator, String action, long timestampMillis,
                        String module, String targetType, String targetId, String reason,
                        String ip, String traceId, String beforeJson, String afterJson) {
    }

    /** 销售趋势报表。 */
    public record SalesReportVO(String date, long amountCent, int orderCount) {
    }

    /** 销售业绩排行报表。 */
    public record SalesRankReportVO(int rank, String name, long amountCent) {
    }

    /** 库存预警报表。 */
    public record StockWarningReportVO(Long skuId, String skuName, int availableQty, int threshold) {
    }
}
