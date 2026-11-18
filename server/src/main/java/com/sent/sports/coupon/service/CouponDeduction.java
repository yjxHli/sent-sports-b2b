package com.sent.sports.coupon.service;

import com.sent.sports.coupon.service.dto.CouponLine;

import java.util.List;

/** 跨模块契约：取价 / 下单只依赖本接口，不碰券模块领域对象（ArchUnit 规则 4）。 */
public interface CouponDeduction {

    /**
     * 券有效且达门槛时返回抵扣额（分），否则 0；不改券状态。
     * <p>传订单行而不是总金额：品类券只对命中 SPU/SKU 的行金额生效。</p>
     */
    long discountFor(long couponId, long customerId, List<CouponLine> lines);

    /** 兼容整单金额调用（全场券）；品类券走明细重载。 */
    default long discountFor(long couponId, long customerId, long goodsCent) {
        return discountFor(couponId, customerId, List.of(new CouponLine(0L, goodsCent)));
    }

    /** 下单成功后核销，重复调用幂等。 */
    void markUsed(long couponId, String orderNo);
}
