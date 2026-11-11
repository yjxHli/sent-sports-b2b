package com.sent.sports.payment.domain;

/**
 * 支付流水聚合（payment 模块）：一次支付意图/结果。
 * 创建时为 CREATED；回调成功置 PAID，失败置 FAILED，退款关单置 CLOSED。
 */
public class PaymentTxn {

    private final String txnNo;
    private final String orderNo;
    private final long amountCent;
    private final String channel;
    private final String idemKey;
    private final long createdAtMillis;
    private PaymentTxnStatus status;
    private String payChannelTradeNo;

    public PaymentTxn(String txnNo, String orderNo, long amountCent, String channel, String idemKey, long createdAtMillis) {
        this.txnNo = txnNo;
        this.orderNo = orderNo;
        this.amountCent = amountCent;
        this.channel = channel;
        this.idemKey = idemKey;
        this.createdAtMillis = createdAtMillis;
        this.status = PaymentTxnStatus.CREATED;
    }

    /** 从持久化行还原聚合（真库仓储使用），保留已落账的状态与渠道交易号，供回调幂等判定。 */
    public static PaymentTxn restore(String txnNo, String orderNo, long amountCent, String channel,
                                     String idemKey, long createdAtMillis, PaymentTxnStatus status,
                                     String payChannelTradeNo) {
        PaymentTxn txn = new PaymentTxn(txnNo, orderNo, amountCent, channel, idemKey, createdAtMillis);
        txn.status = status;
        txn.payChannelTradeNo = payChannelTradeNo;
        return txn;
    }

    public String getTxnNo() {
        return txnNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public long getAmountCent() {
        return amountCent;
    }

    public String getChannel() {
        return channel;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public PaymentTxnStatus getStatus() {
        return status;
    }

    public String getPayChannelTradeNo() {
        return payChannelTradeNo;
    }

    public void markPaid(String payChannelTradeNo) {
        this.status = PaymentTxnStatus.PAID;
        this.payChannelTradeNo = payChannelTradeNo;
    }

    public void markFailed() {
        this.status = PaymentTxnStatus.FAILED;
    }

    public void markClosed() {
        this.status = PaymentTxnStatus.CLOSED;
    }

    public void markRefunded() {
        this.status = PaymentTxnStatus.REFUNDED;
    }
}
