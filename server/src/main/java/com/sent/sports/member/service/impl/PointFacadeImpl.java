package com.sent.sports.member.service.impl;

import com.sent.sports.customer.service.CustomerPointService;
import com.sent.sports.member.domain.PointDeduction;
import com.sent.sports.member.domain.PointRule;
import com.sent.sports.member.service.PointFacade;
import org.springframework.stereotype.Service;

/**
 * 积分门面实现：抵扣上限委托领域 {@link PointDeduction}，退款扣回委托
 * customer 模块的 {@link CustomerPointService} 真实落 {@code point_log}。
 *
 * <p>跨模块只依赖对方 {@code service} 包里的接口，不碰 customer 的 mapper，
 * 符合 ArchUnit 规则 4。</p>
 */
@Service
public class PointFacadeImpl implements PointFacade {

    /** 积分流水来源类型：退款扣回。 */
    private static final String SOURCE_REFUND = "REFUND";

    /**
     * 积分流水来源类型：支付抵扣。
     * <p>不能与发放侧共用 {@code ORDER}：同一笔订单既有抵扣流水（支付时）又有发放流水（完成时），
     * {@code uk_point_source(customer_id, source_type, source_id)} 会把两者判成同一条，后者的
     * {@code INSERT IGNORE} 直接返回 0，用户完成订单就拿不到积分了。</p>
     */
    private static final String SOURCE_ORDER_USE = "ORDER_USE";

    /**
     * 积分流水来源类型：退款退回抵扣积分。
     * <p>与退款侧扣回赠送积分的 {@code REFUND} 必须分开：同一退款单既要扣回赠送积分、
     * 又要退回抵扣积分，共用来源类型会让后者被唯一键判成重放、直接不生效。</p>
     */
    private static final String SOURCE_REFUND_RETURN = "REFUND_RETURN";

    private final CustomerPointService customerPointService;

    public PointFacadeImpl(CustomerPointService customerPointService) {
        this.customerPointService = customerPointService;
    }

    @Override
    public long maxDeductibleCent(int availablePoints, long goodsTotalCent) {
        return PointDeduction.maxDeductibleCent(availablePoints, goodsTotalCent);
    }

    @Override
    public long revokeForRefund(long customerId, String refundNo, long refundCent) {
        long points = PointRule.pointsForCent(refundCent);
        if (points <= 0L) {
            return 0L; // 不足 1 元：没有对应积分可扣，不写空流水
        }
        customerPointService.changePoints(customerId, -points, SOURCE_REFUND, refundNo);
        return points;
    }

    @Override
    public long deductPoints(long customerId, String orderNo, long pointDeductCent) {
        // 抵扣口径是「1 积分 = 1 分」，不能复用上面发放侧的 PointRule（差 100 倍）
        long points = PointDeduction.pointsForDeductCent(pointDeductCent);
        if (points <= 0L) {
            return 0L; // 本单没用积分：不写空流水
        }
        return customerPointService.deductPoints(customerId, points, SOURCE_ORDER_USE, orderNo);
    }

    @Override
    public long deductedPointsForOrder(long customerId, String orderNo) {
        return customerPointService.deductedPoints(customerId, SOURCE_ORDER_USE, orderNo);
    }

    @Override
    public void returnForRefund(long customerId, String refundNo, long points) {
        if (points <= 0L) {
            return; // 没有要退的积分：不写空流水
        }
        // 正增量走 changePoints：金额加回余额，幂等同样交给 uk_point_source
        customerPointService.changePoints(customerId, points, SOURCE_REFUND_RETURN, refundNo);
    }
}
