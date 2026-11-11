package com.sent.sports.order.service;

/**
 * 订单退款门面（跨模块契约）：退款完成后关单（REFUND_DONE → CLOSED）。
 * 供 refund 模块退款回调使用，符合 ArchUnit 规则 4（跨模块仅经 service 接口）。
 */
public interface OrderRefundGateway {
    /** 将指定订单经退款动作迁移至已关闭（CLOSED）。 */
    void closeOnFullRefund(String orderNo);
}
