package com.sent.sports.member.service.impl;

import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.customer.service.CustomerGrowthService;
import com.sent.sports.customer.service.CustomerPointService;
import com.sent.sports.member.domain.MemberTier;
import com.sent.sports.member.domain.PointRule;
import com.sent.sports.member.event.MemberRewardEvent;
import com.sent.sports.member.service.MemberRewardService;
import org.springframework.stereotype.Service;

/**
 * 会员奖励门面实现：订单完成时发放成长值与积分，并经 customer 模块的门面真实落账。
 * <ul>
 *   <li>成长值 → {@link CustomerGrowthService}：{@code growth_log} + {@code customer.growth_value}；</li>
 *   <li>积分 → {@link CustomerPointService}：{@code point_log} + {@code customer.points_balance}
 *       （P0 修复：此前只发事件不落库，自动/手动确认收货的订单都拿不到积分）；</li>
 *   <li>等级按成长值落账后的余额取档（只升不降）。</li>
 * </ul>
 * <p>跨模块只依赖对方 service 接口，不碰 customer 的 mapper，符合 ArchUnit 规则 4。</p>
 */
@Service
public class MemberRewardServiceImpl implements MemberRewardService {

    /** 1 元 = 1 成长值；金额以「分」承载。 */
    private static final long CENT_PER_GROWTH_POINT = 100L;

    /** 积分流水来源类型：订单完成发放。 */
    private static final String SOURCE_ORDER = "ORDER";

    private final DomainEventPublisher eventPublisher;
    private final CustomerGrowthService customerGrowthService;
    private final CustomerPointService customerPointService;

    public MemberRewardServiceImpl(DomainEventPublisher eventPublisher,
                                   CustomerGrowthService customerGrowthService,
                                   CustomerPointService customerPointService) {
        this.eventPublisher = eventPublisher;
        this.customerGrowthService = customerGrowthService;
        this.customerPointService = customerPointService;
    }

    @Override
    public void grantOrderCompleted(long customerId, String orderNo, long payableCent) {
        eventPublisher.publish(new MemberRewardEvent(customerId, orderNo, payableCent));
        long growth = Math.max(1L, payableCent / CENT_PER_GROWTH_POINT);
        long balance = customerGrowthService.grantGrowth(customerId, growth, SOURCE_ORDER, orderNo);
        // 积分与成长值同口径（1 元 = 1 积分），uk_point_source 保证同一订单只发一次
        customerPointService.changePoints(customerId, PointRule.pointsForCent(payableCent),
                SOURCE_ORDER, orderNo);
        // 成长值涨了就必须同步等级：否则会员中心会出现「成长值 16167 却是 LV1」的自相矛盾数据。
        // 口径按落账后的余额取档（重放订单返回的是当前余额，不会误升）；只升不降由条件更新兜底。
        MemberTier tier = MemberTier.calc(balance);
        if (tier != MemberTier.NORMAL) {
            customerGrowthService.promoteLevel(customerId, tier.getCode());
        }
    }
}
