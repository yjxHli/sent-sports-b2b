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

    /**
     * 下单请求：客户、收货地址、幂等键、可用积分、运费规则、商品行。
     *
     * <p>{@code addressId} 不加 {@code @NotNull}：空值要落到用例里抛业务异常
     * （PARAM_INVALID「请先选择收货地址」），前端拿到的错误码与文案才和其它下单校验一致，
     * 而不是被 Web 层 400 拦成框架校验消息。</p>
     */
    public record CreateOrderCmd(
            @NotNull(message = "客户ID不能为空") Long customerId,
            Long addressId,
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

    /**
     * 订单响应：四维度状态（主状态/支付/发货/退款）+ 金额（分 + 元）+ 商品行。
     * <p>商品行是申请退款的必要条件：退款必须逐行提交 {@code skuId} 与行实付，
     * 详情接口若不回传商品行，前端只能瞎猜（曾提交 {@code skuId=0} 被后端判「订单行不存在」→ 400）。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderVO(String orderNo, String status, String payStatus, String shipStatus, String refundStatus,
                          Long payableCent, String payableYuan,
                          Long goodsAmountCent, String goodsAmountYuan,
                          Long freightCent, String freightYuan,
                          Long pointDeductCent, String pointDeductYuan,
                          List<OrderItemLine> items,
                          /** 下单 / 付款 / 成交时间（毫秒，0/null = 未发生），详情页时间轴用 */
                          Long createdAt, Long paidAt, Long completedAt,
                          /** 优惠金额与定制费（分），付款详情里拆开展示 */
                          Long discountCent, Long customFeeCent,
                          /** 收货地址快照（原始 JSON 串，历史单可能为空） */
                          String addressSnapshotJson,
                          /** 最近一次发货的承运商 / 单号 / 发货时间（毫秒） */
                          String carrier, String trackingNo, Long shippedAtMillis,
                          /**
                           * 支付摘要：渠道（BALANCE/ALIPAY/WECHAT/MOCK）、平台流水号、渠道交易号。
                           * <p>淘宝式右栏「订单信息」要写明「支付方式 / 交易号」，这三个字段由
                           * payment 模块的只读门面补齐；未发起支付时全为 null，前端按「未支付」渲染。</p>
                           */
                          String payChannel, String payTxnNo, String tradeNo) {

        /** 兼容下单 / 确认收货等「只回状态不回商品行」的入口。 */
        public OrderVO(String orderNo, String status, String payStatus, String shipStatus, String refundStatus,
                       Long payableCent, String payableYuan,
                       Long goodsAmountCent, String goodsAmountYuan,
                       Long freightCent, String freightYuan,
                       Long pointDeductCent, String pointDeductYuan) {
            this(orderNo, status, payStatus, shipStatus, refundStatus, payableCent, payableYuan,
                    goodsAmountCent, goodsAmountYuan, freightCent, freightYuan,
                    pointDeductCent, pointDeductYuan, List.of(),
                    null, null, null, null, null, null, null, null, null,
                    null, null, null);
        }

        /** 兼容「带回商品行但不需要时间/物流」的调用（详情接口已改走全参构造）。 */
        public OrderVO(String orderNo, String status, String payStatus, String shipStatus, String refundStatus,
                       Long payableCent, String payableYuan,
                       Long goodsAmountCent, String goodsAmountYuan,
                       Long freightCent, String freightYuan,
                       Long pointDeductCent, String pointDeductYuan,
                       List<OrderItemLine> items) {
            this(orderNo, status, payStatus, shipStatus, refundStatus, payableCent, payableYuan,
                    goodsAmountCent, goodsAmountYuan, freightCent, freightYuan,
                    pointDeductCent, pointDeductYuan, items,
                    null, null, null, null, null, null, null, null, null,
                    null, null, null);
        }
    }

    /**
     * 我的订单列表项。
     * <p>带上 {@code createdAt}（毫秒）与 {@code items}：淘宝式订单卡片要显示「下单时间/店铺名/商品行」，
     * 商品行随列表一次返回，前端不必逐单再查详情（N+1）。</p>
     */
    public record OrderListItem(String orderNo, String status, String payStatus, String refundStatus,
                                Long payableCent, String payableYuan,
                                Long createdAt, List<OrderItemLine> items) {
    }

    /**
     * 订单商品行（标题/规格/单价/数量都取快照，商品改价不影响历史订单展示）。
     * <p>{@code linePaidCent} 是这一行的实付金额（行金额 − 分摊优惠），退款申请直接用它作为
     * 行退款上限，避免前端用「单价 × 数量」在折扣单上算出超退。</p>
     * <p>{@code productId} 是商品（SPU）主键：订单卡片点商品名要跳商品详情，
     * 只有 skuId 时拼出的 /product/{skuId} 打不开（详情按 productId 查）。</p>
     */
    public record OrderItemLine(String skuId, String productId, String title, String spec, Integer qty,
                                Long unitPriceCent, String unitPriceYuan,
                                Long linePaidCent, String linePaidYuan) {
    }
}
