package com.sent.sports.order.event;

/**
 * 订单支付成功事件（P3-02）。支付回调用例发布，驱动发货/积分/成长值。
 */
public record OrderPaidEvent(String orderNo, long amountCent) {
}
