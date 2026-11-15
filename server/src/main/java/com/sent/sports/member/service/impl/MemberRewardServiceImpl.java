package com.sent.sports.member.service.impl;

import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.customer.service.CustomerGrowthService;
import com.sent.sports.member.domain.MemberTier;
import com.sent.sports.member.event.MemberRewardEvent;
import com.sent.sports.member.service.MemberRewardService;
import org.springframework.stereotype.Service;

/**
 * 会员奖励门面实现：订单完成时发布 MemberRewardEvent，并经 customer 模块的
 * {@link CustomerGrowthService} 门面真实写入 growth_log + customer.growth_value。
 * <p>跨模块只依赖对方 service 接口，不碰 customer 的 mapper，符合 ArchUnit 规则 4。</p>
 */
@Service
public class MemberRewardServiceImpl implements MemberRewardService {

    /** 1 元 = 1 成长值；金额以「分」承载。 */
    private static final long CENT_PER_GROWTH_POINT = 100L;

    private final DomainEventPublisher eventPublisher;
    private final CustomerGrowthService customerGrowthService;

    public MemberRewardServiceImpl(DomainEventPublisher eventPublisher,
                                   CustomerGrowthService customerGrowthService) {
        this.eventPublisher = eventPublisher;
        this.customerGrowthService = customerGrowthService;
    }

    @Override
    public void grantOrderCompleted(long customerId, String orderNo, long payableCent) {
        eventPublisher.publish(new MemberRewardEvent(customerId, orderNo, payableCent));
        long growth = Math.max(1L, payableCent / CENT_PER_GROWTH_POINT);
        long balance = customerGrowthService.grantGrowth(customerId, growth, "ORDER", orderNo);
        // 成长值涨了就必须同步等级：否则会员中心会出现「成长值 16167 却是 LV1」的自相矛盾数据。
        // 口径按落账后的余额取档（重放订单返回的是当前余额，不会误升）；只升不降由条件更新兜底。
        MemberTier tier = MemberTier.calc(balance);
        if (tier != MemberTier.NORMAL) {
            customerGrowthService.promoteLevel(customerId, tier.getCode());
        }
    }
}
