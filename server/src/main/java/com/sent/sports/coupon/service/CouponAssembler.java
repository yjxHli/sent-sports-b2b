package com.sent.sports.coupon.service;

import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;

/**
 * 行对象 → 领域对象。**唯一映射点**：漏传券型/范围的构造器会让品类券与企业券
 * 悄悄退化成全场券（读侧筛选、抵扣、可用券判定都会一起错），所以不允许各写一份。
 */
public final class CouponAssembler {

    private CouponAssembler() {
    }

    public static Coupon toDomain(CustomerCouponPO po) {
        return new Coupon(po.getId(), po.getCustomerId(), po.getCouponNo(), po.getName(),
                po.getAmountCent(), po.getThresholdCent(),
                po.getCouponType(), po.getDiscountType(), po.getDiscountRate(),
                po.getScopeType(), po.getScopeIds(), po.getCustomerScope(),
                po.getStatus(), po.getSource(), po.getReason(),
                po.getExpireAt(), po.getUsedAt(), po.getOrderNo());
    }
}
