package com.sent.sports.customize.service.impl;

import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.CustomRefundPolicy;
import com.sent.sports.customize.service.CustomizeRefundGateway;
import org.springframework.stereotype.Service;

/**
 * 定制退款判定门面实现：把定制单状态映射成三档处置。
 *
 * <p>映射依据（2026-11-21 用户口径，改这里之前先改测试）：</p>
 * <ul>
 *   <li>WAIT_DRAFT / WAIT_REVIEW / REJECTED / CANCELLED → 可自助退（还没进机器）；</li>
 *   <li>APPROVED / ESCALATED → 可申请但要人工复核（过审未排产，需客服确认是否已备料）；</li>
 *   <li>IN_PRODUCTION / FINISHED / SHIPPED → 绝对拦截（已进生产链路）；</li>
 *   <li>无记录 / NONE → 非定制行，不受此规则约束。</li>
 * </ul>
 *
 * <p>仅依赖 customize 模块内部（仓储端口 / 领域），符合 ArchUnit 规则 4。</p>
 */
@Service
public class CustomizeRefundGatewayImpl implements CustomizeRefundGateway {

    private final CustomizeRepository customizeRepository;

    public CustomizeRefundGatewayImpl(CustomizeRepository customizeRepository) {
        this.customizeRepository = customizeRepository;
    }

    @Override
    public CustomRefundPolicy policyOf(String orderNo, long skuId) {
        return customizeRepository.statusOf(orderNo, skuId)
                .map(CustomizeRefundGatewayImpl::toPolicy)
                .orElse(CustomRefundPolicy.NOT_CUSTOM);
    }

    private static CustomRefundPolicy toPolicy(CustomStatus status) {
        return switch (status) {
            case WAIT_DRAFT, WAIT_REVIEW, REJECTED, CANCELLED -> CustomRefundPolicy.SELF_SERVICE;
            case APPROVED, ESCALATED -> CustomRefundPolicy.MANUAL_REVIEW;
            case IN_PRODUCTION, FINISHED, SHIPPED -> CustomRefundPolicy.BLOCKED;
            case NONE -> CustomRefundPolicy.NOT_CUSTOM;
        };
    }
}
