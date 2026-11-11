package com.sent.sports.payment.gateway;

import java.util.Map;

/**
 * 支付通道端口（P5-33）：用例只依赖此接口，真实通道（微信/支付宝）与模拟通道可互换。
 * <p>回调校验放在通道侧，是因为签名算法、字段顺序、时间戳口径都由通道定义；
 * 业务侧只关心「这笔回调是否可信」。
 */
public interface PayGateway {

    /** 发起支付，返回通道侧交易号与前端拉起支付所需的报文。 */
    PayTicket create(String txnNo, String orderNo, long amountCent, String channel);

    /** 校验回调报文的签名与完整性（不可信即拒绝，不做任何业务变更）。 */
    boolean verify(Map<String, String> callbackParams);

    /**
     * 向通道发起退款（管理端关单全额退款走此入口）。
     *
     * @param txnNo      原支付流水号；余额/授信支付无通道流水时由调用方传构造值
     * @param orderNo    业务订单号
     * @param amountCent 退款金额（分）
     * @return true 表示通道已受理；false 表示通道拒绝，调用方必须回滚整个关单事务
     */
    boolean refund(String txnNo, String orderNo, long amountCent);

    /**
     * 发起支付的结果。
     *
     * @param txnNo  通道交易号（对应 {@code payment_txn.txn_no}）
     * @param payload 前端拉起支付所需的报文（模拟通道为可复现 JSON）
     * @param sign   报文签名
     */
    record PayTicket(String txnNo, String payload, String sign) {
    }
}
