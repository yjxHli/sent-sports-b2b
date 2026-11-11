package com.sent.sports.payment.service;

/**
 * 支付退款门面（跨模块契约）：将订单对应支付流水置为 REFUNDED。
 * 供 refund 模块退款回调使用，符合 ArchUnit 规则 4（跨模块仅经 service 接口）。
 */
public interface PaymentRefundGateway {
    /** 将指定订单的支付流水标记为已退款（幂等：已是 REFUNDED 仍为成功）。 */
    void markRefunded(String orderNo);

    /**
     * 关单全额退款：调用模拟退款网关，受理成功后把支付流水置 REFUNDED。
     * <p>余额/授信支付可能没有通道流水，此时仍走网关但跳过流水写入，不因此阻塞关单；
     * 网关拒绝时抛 BIZ_REJECT，由 {@code @UseCase} 事务回滚全部落账。</p>
     */
    void refund(String orderNo, long amountCent);
}
