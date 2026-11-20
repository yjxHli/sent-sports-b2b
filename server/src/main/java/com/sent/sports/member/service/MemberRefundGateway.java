package com.sent.sports.member.service;

/**
 * 会员退款门面（跨模块契约）：退还退款金额对应的会员积分 / 成长值。
 * 供 refund 模块退款回调使用，符合 ArchUnit 规则 4（跨模块仅经 service 接口）。
 */
public interface MemberRefundGateway {

    /**
     * 扣回该笔退款对应的会员积分（amountCent 为退款金额，单位分）。
     *
     * <p>{@code refundNo} 是幂等键的来源单号：同一退款单被重放（渠道重复通知 / 人工重试）
     * 时不会二次扣回。</p>
     */
    void refundPoints(long customerId, String refundNo, long amountCent);

    /**
     * 该订单实际扣减过的抵扣积分（0 = 没用积分 / 支付未成功过）。
     *
     * <p>以台账为准：{@code orders.point_deduct_cent} 是下单快照，未支付或已取消的订单也会带着
     * 非 0 值，照它退等于凭空发积分。</p>
     */
    long deductedPointsOf(long customerId, String orderNo);

    /**
     * 退款成功：把本单应退的抵扣积分退回客户，落 {@code point_log}（type = REFUND_RETURN）。
     *
     * <p>{@code refundNo} 是幂等键来源：同一退款单重放不会二次退回。</p>
     */
    void returnDeductedPoints(long customerId, String refundNo, long points);
}
