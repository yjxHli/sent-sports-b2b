package com.sent.sports.member.service.impl;

import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.member.event.MemberRefundEvent;
import com.sent.sports.member.service.MemberRefundGateway;
import org.springframework.stereotype.Service;

/**
 * 会员退款门面实现：发布 MemberRefundEvent，由 member 模块监听者在 Phase 5 落库积分回退。
 * 仅依赖 common 与 member 模块内部，符合 ArchUnit 规则 4。
 */
@Service
public class MemberRefundGatewayImpl implements MemberRefundGateway {

    private final DomainEventPublisher eventPublisher;

    public MemberRefundGatewayImpl(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void refundPoints(long customerId, long amountCent) {
        eventPublisher.publish(new MemberRefundEvent(customerId, amountCent));
    }
}
