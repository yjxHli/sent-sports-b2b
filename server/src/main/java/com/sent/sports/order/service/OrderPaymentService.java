package com.sent.sports.order.service;

import com.sent.sports.order.service.dto.OrderPaymentView;

import java.util.Optional;

/**
 * 订单支付门面（跨模块契约）：payment 模块通过此接口读取订单支付态、应用支付成功迁移，
 * 不直接依赖 order 领域类型（ArchUnit 规则 4）。
 */
public interface OrderPaymentService {
    Optional<OrderPaymentView> getView(String orderNo);

    /**
     * 待付款 → 待发货 + 已支付，并把实付金额回写 {@code orders.paid_cent}，发布 OrderPaidEvent；
     * 非待付款状态抛 STATE_CONFLICT(40901)。
     *
     * @param paidCent 本次渠道实付金额（分），必须与回调金额一致
     */
    void markPaid(String orderNo, long paidCent, long paidAtMillis);
}
