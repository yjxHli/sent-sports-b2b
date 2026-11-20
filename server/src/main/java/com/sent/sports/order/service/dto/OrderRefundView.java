package com.sent.sports.order.service.dto;

import java.util.List;

/**
 * 订单退款视图（中立 DTO）：退款项列表 + 运费 + 是否已发货 + 买家 + 实付总额。
 * 供 refund 模块消费，符合 ArchUnit 规则 4。
 *
 * <p>{@code payableCent} 是退款退积分要用的第二条「整单」判据：admin 关单 / 迟到支付的仅退款
 * 只按实付金额退，拿不到商品行全额（见 {@code RefundCalculator.pointsToReturn}）。</p>
 */
public record OrderRefundView(String orderNo, long customerId, long orderId, List<RefundLineView> items,
                              long shippingFeeCent, boolean shipped, long payableCent) {

    /** 兼容只关心退款项的调用方（用例断言）：不涉及实付总额时按 0 处理。 */
    public OrderRefundView(String orderNo, long customerId, long orderId, List<RefundLineView> items,
                           long shippingFeeCent, boolean shipped) {
        this(orderNo, customerId, orderId, items, shippingFeeCent, shipped, 0L);
    }
}
