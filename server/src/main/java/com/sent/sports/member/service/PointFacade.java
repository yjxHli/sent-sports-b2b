package com.sent.sports.member.service;

/**
 * 积分门面（跨模块契约）：封装积分抵扣计算，供 cart/order 调用，
 * 不直接暴露 member 领域类型，满足 ArchUnit 规则 4。
 */
public interface PointFacade {
    /** 本次最多可抵扣金额（分）= min(可用积分折分, 商品总额 × 30%)。 */
    long maxDeductibleCent(int availablePoints, long goodsTotalCent);
}
