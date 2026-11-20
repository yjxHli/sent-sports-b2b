package com.sent.sports.payment.service.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付回调指令：流水号、订单号、支付金额（分）、渠道、渠道交易号、支付完成时间，
 * 以及<b>渠道签过名的原始参数</b>（{@link #notifyParams()}）。
 *
 * <p>业务字段为了可读性单独拆出来，但它们必须与签名的参数同源——因此正常链路只经
 * {@link #fromNotify(Map)} 构造：先拿原始参数，再从中解析业务字段。
 * 验签在用例的最前面完成（plan §7.3 步骤 1），接口层不再「假定已验签」。</p>
 */
public record PayCallbackCmd(String txnNo, String orderNo, long amountCent, String channel,
                            String payChannelTradeNo, long paidAtMillis,
                            Map<String, String> notifyParams) {

    /** 兼容无签名参数的构造入口：验签必然失败，只用于单测构造「伪造回调」。 */
    public PayCallbackCmd(String txnNo, String orderNo, long amountCent, String channel,
                          String payChannelTradeNo, long paidAtMillis) {
        this(txnNo, orderNo, amountCent, channel, payChannelTradeNo, paidAtMillis, Map.of());
    }

    /**
     * 由渠道通知的原始键值参数构造（验签口径的唯一入口）。
     * <p>缺字段一律落 0 / null，交给验签与后续校验拒绝，不在这里抛异常——
     * 「是否可信」由通道签名判定，而不是靠字段完整性。</p>
     */
    public static PayCallbackCmd fromNotify(Map<String, String> params) {
        Map<String, String> copy = params == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        return new PayCallbackCmd(
                copy.get("txnNo"),
                copy.get("orderNo"),
                parseLong(copy.get("amountCent")),
                copy.get("channel"),
                copy.get("payChannelTradeNo"),
                parseLong(copy.get("paidAtMillis")),
                copy);
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
