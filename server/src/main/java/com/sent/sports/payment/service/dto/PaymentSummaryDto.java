package com.sent.sports.payment.service.dto;

/**
 * 支付摘要（跨模块只读视图）：订单详情「订单信息」要写清这笔钱走的是哪个通道、哪笔流水。
 *
 * <p>只暴露四个标识字段，不带金额与原始报文——金额以订单的 {@code payable_cent} 为准，
 * 报文体量大且含渠道敏感字段，不适合外泄给其它模块。</p>
 *
 * @param orderNo    订单号
 * @param channel    支付渠道（BALANCE / ALIPAY / WECHAT / MOCK）
 * @param txnNo      平台支付流水号（payment_txn.txn_no）
 * @param tradeNo    渠道交易号（payment_txn.channel_trade_no，未回调时为空）
 * @param status     流水状态（CREATED / PAID / FAILED / CLOSED / REFUNDED）
 */
public record PaymentSummaryDto(String orderNo, String channel, String txnNo, String tradeNo, String status) {
}
