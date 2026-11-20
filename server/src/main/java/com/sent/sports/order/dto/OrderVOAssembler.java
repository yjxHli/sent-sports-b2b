package com.sent.sports.order.dto;

import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.order.mapper.ShipmentMapper;
import com.sent.sports.order.mapper.po.OrderItemListRow;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.payment.service.dto.PaymentSummaryDto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 订单 VO 的唯一装配点（R13）：PO + 商品行 + 物流 + 支付摘要 → {@link OrderDtos.OrderVO}。
 *
 * <p>此前详情接口一次塞 27 个位置参数（其中一个是裸 {@code null}），加字段就要数一遍参数位置。
 * 现在调用方用 {@link #fromDetail} 或命名 {@link Builder}，金额「分 + 元」与时间「毫秒」的换算
 * 只在这里做一次，列表/详情/下单/确认收货不会再各写一套。</p>
 */
public final class OrderVOAssembler {

    private OrderVOAssembler() {
    }

    /** 详情专用装配：四个数据源由调用方查好，本方法只负责映射与换算。 */
    public static OrderDtos.OrderVO fromDetail(OrderPO o, List<OrderItemListRow> items,
                                               ShipmentMapper.ShipmentRow shipment,
                                               PaymentSummaryDto payment) {
        return builder()
                .orderNo(o.getOrderNo())
                .status(o.getStatus())
                .payStatus(o.getPayStatus())
                .refundStatus(o.getAfterSaleStatus())
                .payable(nonNull(o.getPayableCent()))
                .goodsAmount(nonNull(o.getGoodsAmountCent()))
                .freight(nonNull(o.getFreightCent()))
                .pointDeduct(nonNull(o.getPointDeductCent()))
                .items(items.stream().map(OrderVOAssembler::toItemLine).toList())
                .createdAt(toMillis(o.getCreatedAt()))
                .paidAt(toMillis(o.getPaidAt()))
                .completedAt(toMillis(o.getCompletedAt()))
                .discountCent(o.getDiscountCent())
                .customFeeCent(o.getCustomFeeCent())
                .addressSnapshotJson(o.getAddressSnapshotJson())
                .carrier(shipment == null ? null : shipment.getCarrier())
                .trackingNo(shipment == null ? null : shipment.getTrackingNo())
                .shippedAtMillis(shipment == null ? null : toMillis(shipment.getShippedAt()))
                .payChannel(payment == null ? null : payment.channel())
                .payTxnNo(payment == null ? null : payment.txnNo())
                .tradeNo(payment == null ? null : payment.tradeNo())
                .expireAtMillis(toMillis(o.getExpireAt()))
                .build();
    }

    /** 命名构造器：下单 / 确认收货等精简入口无需再传一长串 null。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 行投影 → 前端商品行：金额一律「分 + 元」，行实付用于退款行上限。 */
    public static OrderDtos.OrderItemLine toItemLine(OrderItemListRow i) {
        return new OrderDtos.OrderItemLine(
                i.getSkuId() == null ? null : String.valueOf(i.getSkuId()),
                i.getProductId() == null ? null : String.valueOf(i.getProductId()),
                i.getTitle(), i.getSpec(), i.getQty(),
                i.getUnitPriceCent(),
                yuan(i.getUnitPriceCent()),
                i.getLinePaidCent(),
                yuan(i.getLinePaidCent()));
    }

    private static long nonNull(Long value) {
        return value == null ? 0L : value;
    }

    /** 金额为 null 时保持 null（{@code @JsonInclude(NON_NULL)} 折叠字段），与旧重载构造器口径一致。 */
    private static String yuan(Long cent) {
        return cent == null ? null : MoneyUtil.toYuan(cent);
    }

    /** 下单时间按系统时区转毫秒（前端统一按毫秒渲染）。 */
    private static Long toMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 流式装配器：cent 与 yuan 成对赋值，避免两处金额口径分叉。 */
    public static final class Builder {

        private String orderNo;
        private String status;
        private String payStatus;
        private String shipStatus;
        private String refundStatus;
        private Long payableCent;
        private Long goodsAmountCent;
        private Long freightCent;
        private Long pointDeductCent;
        private List<OrderDtos.OrderItemLine> items = List.of();
        private Long createdAt;
        private Long paidAt;
        private Long completedAt;
        private Long discountCent;
        private Long customFeeCent;
        private String addressSnapshotJson;
        private String carrier;
        private String trackingNo;
        private Long shippedAtMillis;
        private String payChannel;
        private String payTxnNo;
        private String tradeNo;
        private Long expireAtMillis;

        private Builder() {
        }

        public Builder orderNo(String v) { this.orderNo = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder payStatus(String v) { this.payStatus = v; return this; }
        public Builder shipStatus(String v) { this.shipStatus = v; return this; }
        public Builder refundStatus(String v) { this.refundStatus = v; return this; }

        /** 应付金额（分），同时生成元字符串。 */
        public Builder payable(long cent) { this.payableCent = cent; return this; }
        public Builder goodsAmount(long cent) { this.goodsAmountCent = cent; return this; }
        public Builder freight(long cent) { this.freightCent = cent; return this; }
        public Builder pointDeduct(long cent) { this.pointDeductCent = cent; return this; }

        public Builder items(List<OrderDtos.OrderItemLine> v) { this.items = v == null ? List.of() : v; return this; }
        public Builder createdAt(Long v) { this.createdAt = v; return this; }
        public Builder paidAt(Long v) { this.paidAt = v; return this; }
        public Builder completedAt(Long v) { this.completedAt = v; return this; }
        public Builder discountCent(Long v) { this.discountCent = v; return this; }
        public Builder customFeeCent(Long v) { this.customFeeCent = v; return this; }
        public Builder addressSnapshotJson(String v) { this.addressSnapshotJson = v; return this; }
        public Builder carrier(String v) { this.carrier = v; return this; }
        public Builder trackingNo(String v) { this.trackingNo = v; return this; }
        public Builder shippedAtMillis(Long v) { this.shippedAtMillis = v; return this; }
        public Builder payChannel(String v) { this.payChannel = v; return this; }
        public Builder payTxnNo(String v) { this.payTxnNo = v; return this; }
        public Builder tradeNo(String v) { this.tradeNo = v; return this; }
        public Builder expireAtMillis(Long v) { this.expireAtMillis = v; return this; }

        public OrderDtos.OrderVO build() {
            return new OrderDtos.OrderVO(
                    orderNo, status, payStatus, shipStatus, refundStatus,
                    payableCent, yuan(payableCent),
                    goodsAmountCent, yuan(goodsAmountCent),
                    freightCent, yuan(freightCent),
                    pointDeductCent, yuan(pointDeductCent),
                    items, createdAt, paidAt, completedAt,
                    discountCent, customFeeCent, addressSnapshotJson,
                    carrier, trackingNo, shippedAtMillis,
                    payChannel, payTxnNo, tradeNo, expireAtMillis);
        }
    }
}
