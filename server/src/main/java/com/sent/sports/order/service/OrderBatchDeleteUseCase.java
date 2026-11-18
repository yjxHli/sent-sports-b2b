package com.sent.sports.order.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.order.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端订单列表批量删除：只写 {@code orders.admin_deleted_at}。
 *
 * <p>与客户侧「删除订单」（{@code customer_deleted_at}）完全独立：管理员隐藏一条单不能让客户看不到它，
 * 也不能反向影响。订单金额、支付状态、售后单都不触碰。</p>
 */
@Service
public class OrderBatchDeleteUseCase {

    private final OrderMapper orderMapper;
    private final Clock clock;

    public OrderBatchDeleteUseCase(OrderMapper orderMapper, Clock clock) {
        this.orderMapper = orderMapper;
        this.clock = clock;
    }

    @UseCase
    public int delete(List<String> ids) {
        return orderMapper.batchMarkAdminDeleted(
                BatchIds.normalize(ids), LocalDateTime.now(clock));
    }
}
