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
import com.sent.sports.order.dto.OrderVOAssembler;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.ShipmentMapper;
import com.sent.sports.order.mapper.po.OrderItemListRow;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.dto.OrderPaymentStatusDto;
import com.sent.sports.payment.service.PaymentQueryService;
import com.sent.sports.payment.service.dto.PaymentSummaryDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final ShipmentMapper shipmentMapper;
    /** 支付摘要走 payment 模块的只读门面：跨模块只用对方 service 接口（ArchUnit 规则 4）。 */
    private final PaymentQueryService paymentQueryService;

    public OrderQueryServiceImpl(OrderRepository orderRepository, OrderMapper orderMapper,
                                 ShipmentMapper shipmentMapper, PaymentQueryService paymentQueryService) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.paymentQueryService = paymentQueryService;
    }

    @Override
    public Optional<OrderPaymentStatusDto> getPaymentStatus(String orderNo) {
        return orderRepository.findByOrderNo(orderNo).map(o -> {
            boolean payable = o.getPayStatus() != PayStatus.PAID && !o.getStatus().isTerminal();
            return new OrderPaymentStatusDto(o.getOrderNo(), payable, o.getPayableCent());
        });
    }

    @Override
    public PageResult<OrderDtos.OrderListItem> listMyOrders(Long customerId, String status, String keyword,
                                                            PageQuery pageQuery) {
        if (customerId == null) {
            return PageResult.of(List.of(), 0L, pageQuery.getPage(), pageQuery.getSize());
        }
        // 空白关键字归一为 null：前端清空搜索框时会传 ''，不能让它变成「只匹配空订单号」
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<OrderPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<OrderPO> rows = orderMapper.selectMyPage(page, customerId, status, normalizedKeyword);

        // 商品行一次性批量取回，按 orderId 归组，避免逐单查询
        List<OrderPO> records = rows.getRecords();
        Map<Long, List<OrderItemListRow>> itemsByOrder = records.isEmpty()
                ? Map.of()
                : orderMapper.selectItemsByOrderIds(records.stream().map(OrderPO::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(OrderItemListRow::getOrderId));

        List<OrderDtos.OrderListItem> list = records.stream()
                .map(o -> new OrderDtos.OrderListItem(
                        o.getOrderNo(), o.getStatus(), o.getPayStatus(), o.getAfterSaleStatus(),
                        o.getPayableCent(), MoneyUtil.toYuan(nonNull(o.getPayableCent())),
                        toMillis(o.getCreatedAt()),
                        itemsByOrder.getOrDefault(o.getId(), List.of()).stream()
                                .map(OrderVOAssembler::toItemLine)
                                .toList(),
                        toMillis(o.getExpireAt())))
                .toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    /** 下单时间按系统时区转毫秒（前端统一按毫秒渲染，`JacksonConfig` 会把 Long 序列化成字符串）。 */
    private Long toMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Override
    public OrderDtos.OrderVO getDetail(String orderNo, Long viewerCustomerId) {
        OrderPO o = orderMapper.selectByOrderNo(orderNo);
        if (o == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // P0-02：非管理端（viewer 非空）必须归属一致。放在「查到之后、取明细之前」：
        // 越权请求不会再去读商品行/物流/支付，避免白跑三条查询，也避免错误响应里夹带任何订单信息。
        if (viewerCustomerId != null && !viewerCustomerId.equals(o.getCustomerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该订单");
        }
        // 退款必须逐行（skuId + 行实付），详情与列表同口径带回商品行
        List<OrderItemListRow> itemRows = o.getId() == null
                ? List.of()
                : orderMapper.selectItemsByOrderIds(List.of(o.getId()));
        // 订单详情右侧「订单信息」要展示最近一次物流；未发货时整行为空态
        ShipmentMapper.ShipmentRow shipment = o.getId() == null
                ? null
                : shipmentMapper.selectLatestByOrderId(o.getId());
        // 支付方式 / 交易号：未发起支付时为空，前端按「未支付」空态渲染
        PaymentSummaryDto payment = paymentQueryService.findSummary(o.getOrderNo()).orElse(null);
        // 商城端 5 分钟倒计时的时间源与 OrderTimeoutJob 一致（同一个 orders.expire_at），
        // 换算与字段映射统一交给 Assembler，避免 27 个位置参数错位。
        return OrderVOAssembler.fromDetail(o, itemRows, shipment, payment);
    }

    private long nonNull(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 评价资格（V55）：交给 SQL 一次判定「含此商品 + 已完成」，本层只做入参防御。
     * <p>非法入参直接返回空而不是抛异常——评价接口自己会先把入参校验掉，
     * 这里再抛一遍只会让同一个错误出现两套文案。</p>
     */
    @Override
    public Optional<String> findCompletedOrderNoForReview(long customerId, long productId, Long skuId) {
        if (customerId <= 0 || productId <= 0) {
            return Optional.empty();
        }
        String orderNo = orderMapper.selectCompletedOrderNoForReview(customerId, productId, skuId);
        return orderNo == null || orderNo.isBlank() ? Optional.empty() : Optional.of(orderNo);
    }

    @Override
    public boolean isClosedWithFullRefund(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return false;
        }
        OrderPO po = orderMapper.selectByOrderNo(orderNo);
        return po != null
                && OrderStatus.CLOSED.name().equals(po.getStatus())
                && PayStatus.FULL_REFUNDED.name().equals(po.getPayStatus());
    }
}
