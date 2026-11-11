package com.sent.sports.order.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sent.sports.order.service.dto.ShipLineCmd;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 订单管理接口 Web 层 DTO（P4-39）。
 * 代客下单、拆单、发货、关单的请求与视图（占位，P5 接入实体）。
 */
public final class AdminOrderDtos {

    private AdminOrderDtos() {
    }

    /** 代客下单单行。 */
    public record AgentOrderItemCmd(
            @NotNull(message = "SKU不能为空") Long skuId,
            @Positive(message = "数量必须大于0") int qty,
            String customerTypeCode) {
    }

    /** 代客下单请求。 */
    public record AgentCreateCmd(
            @NotNull(message = "客户ID不能为空") Long customerId,
            @NotBlank(message = "备注不能为空") String remark,
            @NotNull(message = "商品行不能为空") List<AgentOrderItemCmd> items) {
    }

    /** 拆单行。 */
    public record SplitLineCmd(
            @NotNull(message = "SKU不能为空") Long skuId,
            @Positive(message = "数量必须大于0") int qty) {
    }

    /** 拆单请求。 */
    public record SplitCmd(@NotNull(message = "拆单行不能为空") List<SplitLineCmd> lines) {
    }

    /** 发货请求：承运商、物流单号、逐行发货数量。 */
    public record ShipCmd(String carrier, String trackingNo, List<ShipLineCmd> lines) {
    }

    /** 关单请求：原因（P5 附确认令牌）。 */
    public record CloseCmd(@NotBlank(message = "关单原因不能为空") String reason) {
    }

    /** 订单管理视图（中性，不依赖 order 领域类型）。 */
    public record AdminOrderVO(String orderNo, String status, long payableCent, String payableYuan) {
    }

    /**
     * 订单行明细（管理端详情）。
     * <p>商品名/规格取下单时的快照字段，不现查商品表，避免历史订单被改价改名后对不上；
     * {@code customStatus} 由 {@code custom_order} 左联得到，非定制行回填 NONE。</p>
     */
    public record AdminOrderLineVO(Long skuId, String skuName, String spec, Integer qty,
                                   Long unitPriceCent, Integer shippedQty, String customStatus) {
    }

    /**
     * 订单详情（管理端）：四维度状态 + 金额（分）+ 下单时间（毫秒）+ 订单行。
     * <p>字段名严格对齐 {@code web-admin/src/views/order/detail.vue} 与 {@code OrderVO}；
     * 金额一律 BIGINT 分，时间由 LocalDateTime 按系统默认时区换算毫秒，前端统一走 fmtMoney / fmtDateTime。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminOrderDetailVO(
            String orderNo, String status, String payStatus, String shipStatus, String refundStatus,
            Long customerId, Long createdAt,
            Long goodsAmountCent, Long freightCent, Long pointDeductCent,
            Long payableCent, Long paidCent,
            List<AdminOrderLineVO> lines) {
    }
}
