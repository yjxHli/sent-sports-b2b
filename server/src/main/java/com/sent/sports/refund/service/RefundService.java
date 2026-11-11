package com.sent.sports.refund.service;

/**
 * 退款门面（跨模块契约）：payment 等模块通过此接口生成退款单，不直接依赖 refund 领域类型。
 */
public interface RefundService {
    /** 生成仅退款单（如 A2：已取消订单的迟到支付回调）。 */
    void createOnlyRefund(String orderNo, long amountCent, String reason);

    /**
     * 管理端关单的全额退款：先调模拟退款网关，受理后真库落一张 REFUNDED 退款单。
     *
     * @param orderId    订单主键（refund_order.order_id 是外键形态，不是业务单号）
     * @param amountCent 实退金额（分）
     * @return 退款单号
     */
    String createCloseRefund(String orderNo, long orderId, long amountCent, String reason);
}
