package com.sent.sports.member.service;

/**
 * 会员奖励门面（跨模块契约）：order 模块确认收货完成后经此发放成长值与积分，
 * 不直接依赖 member 领域类型（ArchUnit 规则 4）。
 */
public interface MemberRewardService {
    /** 订单完成：按应付金额发放成长值与积分。 */
    void grantOrderCompleted(long customerId, String orderNo, long payableCent);
}
