package com.sent.sports.member.service;

/**
 * 会员退款门面（跨模块契约）：退还退款金额对应的会员积分 / 成长值。
 * 供 refund 模块退款回调使用，符合 ArchUnit 规则 4（跨模块仅经 service 接口）。
 */
public interface MemberRefundGateway {
    /** 退还指定会员在退款金额对应的积分（amountCent 为退款金额，单位分）。 */
    void refundPoints(long customerId, long amountCent);
}
