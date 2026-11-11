package com.sent.sports.customer.service;

/**
 * 成长值发上门面（跨模块契约）：member / order 模块经此接口完成客户成长值真实落账，
 * 不直接依赖 customer 模块的 mapper（ArchUnit 规则 4）。
 */
public interface CustomerGrowthService {

    /**
     * 记一笔成长值流水并累加客户余额。
     *
     * @param sourceType 来源类型，如 ORDER
     * @param sourceId   来源单号（订单号），与 sourceType 共同构成幂等键
     * @return 落账后的成长值余额；客户不存在或该来源已记过流水时返回当前余额
     */
    long grantGrowth(long customerId, long delta, String sourceType, String sourceId);
}
