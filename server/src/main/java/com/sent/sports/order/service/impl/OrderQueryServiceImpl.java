package com.sent.sports.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.domain.PayStatus;
import com.sent.sports.order.dto.OrderDtos;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.dto.OrderPaymentStatusDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 订单查询门面：支付态从领域仓储读取；列表/详情从 P4 空桩换成 MyBatis-Plus 真分页查询。
 *
 * <p>用户端列表按 {@code customer_id} 过滤（主体由 Controller 从 CurrentUser 传入），
 * 金额统一以「分」读取并附元字符串，避免前端各自除 100。</p>
 */
@Service
public class OrderQueryServiceImpl implements OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderQueryServiceImpl(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    public Optional<OrderPaymentStatusDto> getPaymentStatus(String orderNo) {
        return orderRepository.findByOrderNo(orderNo).map(o -> {
            boolean payable = o.getPayStatus() != PayStatus.PAID && !o.getStatus().isTerminal();
            return new OrderPaymentStatusDto(o.getOrderNo(), payable, o.getPayableCent());
        });
    }

    @Override
    public PageResult<OrderDtos.OrderListItem> listMyOrders(Long customerId, PageQuery pageQuery) {
        if (customerId == null) {
            return PageResult.of(List.of(), 0L, pageQuery.getPage(), pageQuery.getSize());
        }
        Page<OrderPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<OrderPO> rows = orderMapper.selectMyPage(page, customerId);
        List<OrderDtos.OrderListItem> list = rows.getRecords().stream()
                .map(o -> new OrderDtos.OrderListItem(
                        o.getOrderNo(), o.getStatus(), o.getPayStatus(), o.getAfterSaleStatus(),
                        o.getPayableCent(), MoneyUtil.toYuan(nonNull(o.getPayableCent()))))
                .toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public OrderDtos.OrderVO getDetail(String orderNo) {
        OrderPO o = orderMapper.selectByOrderNo(orderNo);
        if (o == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return new OrderDtos.OrderVO(
                o.getOrderNo(), o.getStatus(), o.getPayStatus(), null, o.getAfterSaleStatus(),
                o.getPayableCent(), MoneyUtil.toYuan(nonNull(o.getPayableCent())),
                o.getGoodsAmountCent(), MoneyUtil.toYuan(nonNull(o.getGoodsAmountCent())),
                o.getFreightCent(), MoneyUtil.toYuan(nonNull(o.getFreightCent())),
                o.getPointDeductCent(), MoneyUtil.toYuan(nonNull(o.getPointDeductCent())));
    }

    private long nonNull(Long value) {
        return value == null ? 0L : value;
    }
}
