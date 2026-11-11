package com.sent.sports.payment.service.dto;

/**
 * 支付回调结果：状态（SUCCESS / ALREADY_PAID / MISMATCH / REFUNDED）+ 订单号 + 流水号。
 */
public record PayCallbackResult(String status, String orderNo, String txnNo) {
    public static PayCallbackResult success(PayCallbackCmd c) {
        return new PayCallbackResult("SUCCESS", c.orderNo(), c.txnNo());
    }

    public static PayCallbackResult alreadyPaid(PayCallbackCmd c) {
        return new PayCallbackResult("ALREADY_PAID", c.orderNo(), c.txnNo());
    }

    public static PayCallbackResult mismatch(PayCallbackCmd c) {
        return new PayCallbackResult("MISMATCH", c.orderNo(), c.txnNo());
    }

    public static PayCallbackResult refunded(PayCallbackCmd c) {
        return new PayCallbackResult("REFUNDED", c.orderNo(), c.txnNo());
    }
}
