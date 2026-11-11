package com.sent.sports.order.service;

import com.sent.sports.order.service.dto.OrderRefundView;

import java.util.Optional;

/**
 * 订单退款视图门面（跨模块契约）：refund 模块经此读取退款项，不直接依赖 order 领域类型（ArchUnit 规则 4）。
 */
public interface OrderRefundViewService {
    Optional<OrderRefundView> getView(String orderNo);
}
