package com.sent.sports.finance.domain;

/**
 * 回款冲销留痕（plan §7.7 / spec §6.8）：记录回款单被冲销的轨迹（谁、何时、原因）。
 * 纯 POJO，无 Spring 依赖。
 */
public class PaymentReverse {

    private final String id;
    private final String paymentNo;
    private final long operatorId;
    private final String reason;
    private final long createdAtMillis;

    public PaymentReverse(String id, String paymentNo, long operatorId, String reason, long createdAtMillis) {
        this.id = id;
        this.paymentNo = paymentNo;
        this.operatorId = operatorId;
        this.reason = reason;
        this.createdAtMillis = createdAtMillis;
    }

    public String getId() {
        return id;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public long getOperatorId() {
        return operatorId;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
}
