package com.sent.sports.order.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderAction;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.domain.PayStatus;
import com.sent.sports.order.event.OrderPaidEvent;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.OrderPaymentService;
import com.sent.sports.order.service.dto.OrderPaymentView;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * 订单支付门面实现：从仓储加载聚合，映射中立视图；支付成功时经状态机迁移并发布事件。
 * 仅依赖 order 模块内部（仓储/领域）与 common，符合 ArchUnit 规则 4。
 */
@Service
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public OrderPaymentServiceImpl(OrderRepository orderRepository, DomainEventPublisher eventPublisher,
                                   Clock clock) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public Optional<OrderPaymentView> getView(String orderNo) {
        return orderRepository.findByOrderNo(orderNo).map(o ->
                new OrderPaymentView(o.getOrderNo(), o.getStatus().name(), o.getPayStatus().name(), o.getPayableCent()));
    }

    @Override
    public void markPaid(String orderNo, long paidCent, long paidAtMillis) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单不存在"));
        if (order.getStatus() != OrderStatus.WAIT_PAY) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "订单不可支付");
        }
        order.transit(OrderAction.PAY); // WAIT_PAY -> WAIT_SHIP
        order.setPayStatus(PayStatus.PAID);
        // 实付金额是订单头的一部分：经聚合回写，updateHeader 会同步写 orders.paid_cent
        order.setPaidCent(paidCent);
        // 支付时间落库到 orders.paid_at：销售趋势报表以该列为时间基准，
        // 此前回调链路未写入导致真实支付订单全部漏统计（paid_at 恒为 NULL）。
        order.setPaidAtMillis(paidAtMillis > 0 ? paidAtMillis : clock.instant().toEpochMilli());
        orderRepository.save(order);
        eventPublisher.publish(new OrderPaidEvent(order.getOrderNo(), order.getPayableCent()));
    }
}
