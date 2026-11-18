package com.sent.sports.coupon.service;

/** 跨模块契约：注册成功后发放新用户券包（认证模块只依赖本接口）。 */
public interface RegisterCouponGrant {

    void grant(long customerId);
}
