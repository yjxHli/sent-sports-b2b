package com.sent.sports.refund.domain;

/**
 * 退款单聚合（refund 模块）：仅退款（ONLY_REFUND）/ 售后退款等。
 * A2 场景：订单已取消却收到支付回调，生成 ONLY_REFUND（原因 PAID_AFTER_CANCEL）并置 REFUNDING。
 */
public class RefundOrder {

    private final String refundNo;
    private final String orderNo;
    private final long orderId;
    private final long amountCent;
    private final String type;
    private final String reason;
    private RefundStatus status;
    private final long createdAtMillis;
    private int appealCount;

    public RefundOrder(String refundNo, String orderNo, long orderId, long amountCent, String type, String reason,
                      RefundStatus status, long createdAtMillis) {
        this.refundNo = refundNo;
        this.orderNo = orderNo;
        this.orderId = orderId;
        this.amountCent = amountCent;
        this.type = type;
        this.reason = reason;
        this.status = status;
        this.createdAtMillis = createdAtMillis;
        this.appealCount = 0;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public long getOrderId() {
        return orderId;
    }

    public long getAmountCent() {
        return amountCent;
    }

    public String getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public void setStatus(RefundStatus status) {
        this.status = status;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public int getAppealCount() {
        return appealCount;
    }

    public void setAppealCount(int appealCount) {
        this.appealCount = appealCount;
    }
}
