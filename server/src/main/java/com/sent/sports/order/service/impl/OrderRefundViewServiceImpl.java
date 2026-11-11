package com.sent.sports.order.service.impl;

import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderItem;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.order.service.dto.RefundLineView;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 订单退款视图门面实现：从仓储加载聚合，映射中立视图（行实付取 lineTotalCent，已退额 Phase 5 接入退款仓储后回填）。
 * 仅依赖 order 模块内部与 common，符合 ArchUnit 规则 4。
 */
@Service
public class OrderRefundViewServiceImpl implements OrderRefundViewService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderRefundViewServiceImpl(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    public Optional<OrderRefundView> getView(String orderNo) {
        return orderRepository.findByOrderNo(orderNo).map(o -> {
            Long orderId = orderMapper.selectByOrderNo(orderNo).getId();
            List<RefundLineView> items = o.getItems().stream()
                    .map(i -> new RefundLineView(i.getSkuId(), i.getLineTotalCent(), 0L))
                    .toList();
            boolean shipped = o.getItems().stream().anyMatch(i -> i.getShippedQty() > 0);
            return new OrderRefundView(o.getOrderNo(), o.getCustomerId(), orderId, items, o.getFreightCent(), shipped);
        });
    }
}
