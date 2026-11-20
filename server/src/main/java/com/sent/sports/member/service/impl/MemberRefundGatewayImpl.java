package com.sent.sports.member.service.impl;

import com.sent.sports.member.service.MemberRefundGateway;
import com.sent.sports.member.service.PointFacade;
import org.springframework.stereotype.Service;

/**
 * 会员退款门面实现：<b>同步</b>扣回积分。
 *
 * <p>P0 修复（审计 R4）：这里原先只 {@code eventPublisher.publish(new MemberRefundEvent(...))}，
 * 而全仓既没有订阅者、也没有异步分发——事件发出去连异常都没有，退款回调却把它当成
 * 「积分已回退」继续往下走，结果是钱退了、积分没扣。
 * 现在改为直接调用 {@link PointFacade#revokeForRefund}：同事务、可回滚、失败会抛。</p>
 *
 * <p>仅依赖 member 模块内部的 service 接口，符合 ArchUnit 规则 4。</p>
 */
@Service
public class MemberRefundGatewayImpl implements MemberRefundGateway {

    private final PointFacade pointFacade;

    public MemberRefundGatewayImpl(PointFacade pointFacade) {
        this.pointFacade = pointFacade;
    }

    @Override
    public void refundPoints(long customerId, String refundNo, long amountCent) {
        pointFacade.revokeForRefund(customerId, refundNo, amountCent);
    }

    @Override
    public long deductedPointsOf(long customerId, String orderNo) {
        return pointFacade.deductedPointsForOrder(customerId, orderNo);
    }

    @Override
    public void returnDeductedPoints(long customerId, String refundNo, long points) {
        pointFacade.returnForRefund(customerId, refundNo, points);
    }
}
