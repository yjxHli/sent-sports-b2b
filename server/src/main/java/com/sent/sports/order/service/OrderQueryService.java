package com.sent.sports.order.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.order.dto.OrderDtos;
import com.sent.sports.order.service.dto.OrderPaymentStatusDto;

import java.util.Optional;

/**
 * 订单查询门面（跨模块契约）：payment 等模块通过此接口获取订单支付态中立视图，
 * 不直接依赖 order 领域类型（ArchUnit 规则 4）。
 */
public interface OrderQueryService {
    Optional<OrderPaymentStatusDto> getPaymentStatus(String orderNo);

    /** 我的订单：按当前用户 + 分页（P4 桩，P5 接入仓储后替换）。 */
    PageResult<OrderDtos.OrderListItem> listMyOrders(Long customerId, PageQuery pageQuery);

    /** 订单详情：四维度状态 + 金额（P4 桩，P5 接入仓储后替换）。 */
    OrderDtos.OrderVO getDetail(String orderNo);
}
