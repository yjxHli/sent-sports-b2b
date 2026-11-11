package com.sent.sports.order.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderAction;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.OrderRefundGateway;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 订单退款门面实现：加载订单、经状态机 REFUND_DONE 迁移至 CLOSED 并落库。
 * 仅依赖 order 模块内部（仓储端口 / 领域），符合 ArchUnit 规则 4。
 */
@Service
public class OrderRefundGatewayImpl implements OrderRefundGateway {

    private final OrderRepository orderRepository;

    public OrderRefundGatewayImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public void closeOnFullRefund(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单不存在"));
        order.transit(OrderAction.REFUND_DONE);
        orderRepository.save(order);
    }
}
