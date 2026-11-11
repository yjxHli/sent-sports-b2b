package com.sent.sports.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sent.sports.common.dto.TierDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 订单接口 Web 层 DTO（P4-15）。
 * 请求体承载外部入参并带校验注解；响应体 {@link OrderVO} 暴露四维度状态与金额（分 + 元）。
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    /** 下单请求：客户、幂等键、可用积分、运费规则、商品行。 */
    public record CreateOrderCmd(
            @NotNull(message = "客户ID不能为空") Long customerId,
            @NotBlank(message = "幂等键不能为空") String idemKey,
            int availablePoints,
            int freightFreeThresholdCent,
            int freightFeeCent,
            @NotNull(message = "商品行不能为空") List<OrderItemCmd> items) {
    }

    /** 下单单行：携带取价所需中立参数（对齐 order.service.dto.OrderLineCmd）。 */
    public record OrderItemCmd(
            @NotNull(message = "SKU不能为空") Long skuId,
            @Positive(message = "数量必须大于0") int qty,
            String customerTypeCode,
            int memberDiscountRate,
            int agreementDiscountRate,
            boolean allowStacking,
            List<TierDto> ladderTiers,
            Boolean customFlag) {

        /** 兼容既有七参调用（无定制标记）。 */
        public OrderItemCmd(Long skuId, int qty, String customerTypeCode,
                            int memberDiscountRate, int agreementDiscountRate,
                            boolean allowStacking, List<TierDto> ladderTiers) {
            this(skuId, qty, customerTypeCode, memberDiscountRate, agreementDiscountRate,
                    allowStacking, ladderTiers, null);
        }
    }

    /** 确认收货请求：逐行收货数量。 */
    public record ConfirmCmd(@NotNull(message = "收货行不能为空") List<ConfirmLineCmd> lines) {
    }

    /** 确认收货单行。 */
    public record ConfirmLineCmd(
            @NotNull(message = "SKU不能为空") Long skuId,
            @Positive(message = "数量必须大于0") int qty) {
    }

    /** 订单响应：四维度状态（主状态/支付/发货/退款）+ 金额（分 + 元）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderVO(String orderNo, String status, String payStatus, String shipStatus, String refundStatus,
                          Long payableCent, String payableYuan,
                          Long goodsAmountCent, String goodsAmountYuan,
                          Long freightCent, String freightYuan,
                          Long pointDeductCent, String pointDeductYuan) {
    }

    /** 我的订单列表项。 */
    public record OrderListItem(String orderNo, String status, String payStatus, String refundStatus,
                                Long payableCent, String payableYuan) {
    }
}
