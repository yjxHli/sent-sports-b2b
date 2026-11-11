package com.sent.sports.member.event;

/**
 * 会员退款事件（P3-23）：退款成功后回退积分 / 成长值。由 member 模块监听者在 Phase 5 持久化，
 * 此处仅作为跨模块解耦载体。
 */
public record MemberRefundEvent(long customerId, long amountCent) {
}
