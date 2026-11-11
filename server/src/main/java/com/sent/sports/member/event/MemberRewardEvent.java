package com.sent.sports.member.event;

/**
 * 会员奖励事件（P3-17）：订单完成后发放成长值与积分。由 member 模块的监听者在 Phase 5 持久化，
 * 此处仅作为跨模块解耦的载体。
 */
public record MemberRewardEvent(long customerId, String orderNo, long payableCent) {
}
