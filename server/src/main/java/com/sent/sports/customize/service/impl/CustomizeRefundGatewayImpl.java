package com.sent.sports.customize.service.impl;

import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.CustomizeRefundGateway;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 定制退款判定门面实现：IN_PRODUCTION / FINISHED / SHIPPED 状态视为已进入生产，不可退款；无定制记录视为可退。
 * 仅依赖 customize 模块内部（仓储端口 / 领域），符合 ArchUnit 规则 4。
 */
@Service
public class CustomizeRefundGatewayImpl implements CustomizeRefundGateway {

    private final CustomizeRepository customizeRepository;

    public CustomizeRefundGatewayImpl(CustomizeRepository customizeRepository) {
        this.customizeRepository = customizeRepository;
    }

    @Override
    public boolean isRefundBlocked(String orderNo, long skuId) {
        return customizeRepository.statusOf(orderNo, skuId)
                .map(s -> s == CustomStatus.IN_PRODUCTION || s == CustomStatus.FINISHED || s == CustomStatus.SHIPPED)
                .orElse(false);
    }
}
