package com.sent.sports.order.service.impl;

import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.service.OrderJobQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 订单任务查询实现：mapper 只在本模块内使用，对外仅暴露订单号。 */
@Service
public class OrderJobQueryServiceImpl implements OrderJobQueryService {

    private final OrderMapper orderMapper;

    public OrderJobQueryServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public List<String> findAwaitingReceiveOrderNos(LocalDateTime before, int limit) {
        return orderMapper.selectAwaitingReceiveBefore(before, limit).stream()
                .map(OrderPO::getOrderNo).toList();
    }

    @Override
    public List<String> findTimeoutUnpaidOrderNos(LocalDateTime now, int limit) {
        return orderMapper.selectTimeoutUnpaid(now, limit).stream()
                .map(OrderPO::getOrderNo).toList();
    }
}
