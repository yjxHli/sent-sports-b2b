package com.sent.sports.stock.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 库存管理接口 Web 层 DTO（P4-36）。
 * 请求体承载外部入参并带校验注解；响应体暴露给前端。
 * 操作人（operatorId）由 P5 {@code @CurrentUserArg} 注入，本阶段以占位常量传入用例（见控制器）。
 */
public final class AdminStockDtos {

    private AdminStockDtos() {
    }

    /** 手工入库请求：SKU + 数量 + 原因 + 备注。 */
    public record InboundCmd(
            @NotNull(message = "SKU不能为空") Long skuId,
            @Positive(message = "入库数量必须大于0") long qty,
            @NotBlank(message = "入库原因不能为空") String reason,
            String remark) {
    }

    /** 报损请求：SKU + 数量 + 原因 + 备注。 */
    public record LossCmd(
            @NotNull(message = "SKU不能为空") Long skuId,
            @Positive(message = "报损数量必须大于0") long qty,
            @NotBlank(message = "报损原因不能为空") String reason,
            String remark) {
    }

    /** 创建盘点单请求：SKU（账面数量由用例取库存台账）。 */
    public record CheckCreateCmd(@NotNull(message = "SKU不能为空") Long skuId) {
    }

    /** 录入实盘数请求：实盘数量。 */
    public record CheckCountCmd(@NotNull(message = "实盘数量不能为空") Long actualQty) {
    }

    /** 审批盘点请求：确认令牌（P5 由确认令牌拦截器校验，此处仅透传占位）。 */
    public record CheckApproveCmd(String confirmToken) {
    }

    /** 入库响应。 */
    public record InboundVO(long skuId, long afterPhysicalQty, long afterAvailableQty) {
    }

    /** 报损响应。 */
    public record LossVO(long skuId, long afterPhysicalQty, long afterAvailableQty) {
    }

    /** 创建盘点单响应。 */
    public record CheckVO(String checkNo, long skuId, long bookQty, String status) {
    }

    /** 录入实盘响应。 */
    public record CheckCountVO(String checkNo, long bookQty, long actualQty, long diffQty, String status) {
    }

    /** 审批盘点响应。 */
    public record CheckApproveVO(String checkNo, String status, long afterPhysicalQty) {
    }

    /** 库存台账行：四口径 + 可用量 + 安全库存，可用量由服务层统一算，前端不再自己减。 */
    public record StockRow(long skuId, String skuName, int physicalQty, int lockedQty,
                           int pendingQty, int frozenQty, int availableQty, int safeQty,
                           boolean warning) {
    }

    /** 库存流水行：时间用毫秒，字段名与前端 StockFlowVO 对齐。 */
    public record FlowRow(long flowId, String bizType, String bizNo, long skuId, int qty,
                          int beforeQty, int afterQty, String reason, long createdAt) {
    }
}
