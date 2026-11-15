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

    /**
     * 按成长值提升客户等级码（只升不降）。
     * <p>成长值累加后必须同步等级，否则读面会出现「成长值 16167 / 等级 LV1」这种自相矛盾的数据。
     * 条件更新保证：已授级或已处于更高档时返回 0 行，不会把人工授级压回去。</p>
     *
     * @param levelCode 目标等级码（NORMAL / LV1 / LV2 / LV3）
     * @return true = 真的提升了；false = 本就不低于该档或客户不存在
     */
    boolean promoteLevel(long customerId, String levelCode);
}
