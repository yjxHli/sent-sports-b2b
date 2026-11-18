package com.sent.sports.coupon.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.coupon.mapper.CouponMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端发放记录批量删除：只打 {@code customer_coupon.deleted}。
 *
 * <p>删除的是「这条发放记录」：它会从发放列表与客户的「我的优惠券」里消失，
 * 但已使用券对应的订单折扣额、退款口径都不改（那是历史事实）。</p>
 */
@Service
public class CouponIssueAdminUseCase {

    private final CouponMapper couponMapper;

    public CouponIssueAdminUseCase(CouponMapper couponMapper) {
        this.couponMapper = couponMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return couponMapper.batchMarkDeleted(BatchIds.normalize(ids));
    }
}
