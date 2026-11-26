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

    /**
     * 员工表单。
     * <p>{@code username} 落 {@code sys_staff.staff_no}（登录名）；{@code phone} 是登录与找回的必备信息，
     * 表上是 NOT NULL + 唯一键，新增时必须给值。</p>
     */
    public record StaffForm(
            @NotBlank(message = "账号不能为空") String username,
            @NotBlank(message = "姓名不能为空") String name,
            String phone,
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

    /**
     * 员工视图。
     * <p>{@code username} 取 {@code staff_no}（登录名）；{@code phone} 供编辑回填；
     * {@code enabled} 由 {@code status = 'ACTIVE'} 派生，供列表状态标签使用。</p>
     */
    public record StaffVO(Long staffId, String username, String name, String roleCode,
                          String phone, boolean enabled) {
    }

    /** 角色视图。 */
    public record RoleVO(String roleCode, String roleName, List<String> permCodes) {
    }

    /** 参数视图。 */
    public record ParamVO(String paramKey, String paramValue) {
    }

    /**
     * 配置中心行（系统配置中心，2026-11-25）：把键值对翻译成运营能直接读的一项配置。
     *
     * @param configKey    原始键名（{@code sys_config.config_key}），前端以小字灰色展示
     * @param configName   中文名（字典提供；字典里没有的键回落为键名本身）
     * @param description  一句话说明「改这个值会发生什么」，编辑弹窗必须展示
     * @param groupName    业务分组（订单与交易 / 库存与商品 / 财务与开票 / 会员与积分）
     * @param configValue  原始值，弹窗回填与提交都用它
     * @param displayValue 展示值（{@code 30 分钟} / {@code ¥50,000.00} / {@code 开启}）
     * @param unit         单位文案，弹窗输入框后缀用它
     * @param valueType    NUMBER / MONEY_CENT / PERCENT / BOOLEAN / TEXT，前端据此选控件
     * @param remark       库里的备注（历史遗留字段，仅作补充说明）
     */
    public record ConfigVO(String configKey, String configName, String description, String groupName,
                           String configValue, String displayValue, String unit, String valueType,
                           String remark) {
    }

    /** 修改配置值入参：键名在路径上，这里只收值。 */
    public record ConfigValueForm(@NotBlank(message = "配置值不能为空") String configValue) {
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
