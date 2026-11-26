package com.sent.sports.coupon.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 用户删除「我的优惠券」里已使用/已过期的券（2026-11-26 用户需求）。
 *
 * <p>三条口径：</p>
 * <ul>
 *   <li><b>只软删</b>：复用 customer_coupon.deleted，不动 status / 订单折扣额——那些是历史事实；</li>
 *   <li><b>只删终态</b>：待使用的券可能马上要用，拒绝删除；过期态由 expire_at 派生，不落库；</li>
 *   <li><b>越权按不存在</b>：他人券与不存在的券都返回 404，不泄露「这张券存在」；重复删除按成功（幂等）。</li>
 * </ul>
 */
@Service
public class DeleteMyCouponUseCase {

    private final CouponMapper couponMapper;
    private final Clock clock;

    public DeleteMyCouponUseCase(CouponMapper couponMapper, Clock clock) {
        this.couponMapper = couponMapper;
        this.clock = clock;
    }

    /** @param customerId 当前登录客户；0/负数表示未登录或管理端令牌，一律拒绝 */
    @UseCase
    public void delete(long couponId, long customerId) {
        if (customerId <= 0L || couponId <= 0L) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        CustomerCouponPO po = couponMapper.selectForOwnerCheck(couponId);
        if (po == null || po.getCustomerId() != customerId) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (po.getDeleted() != 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean used = Coupon.USED.equals(po.getStatus());
        boolean expired = Coupon.UNUSED.equals(po.getStatus())
                && po.getExpireAt() != null && po.getExpireAt().isBefore(now);
        if (!used && !expired) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "仅已使用或已过期的优惠券可以删除");
        }
        // 条件更新影响 0 行：并发下已被删过，按幂等成功处理
        couponMapper.softDeleteMine(couponId, customerId, now);
    }
}
