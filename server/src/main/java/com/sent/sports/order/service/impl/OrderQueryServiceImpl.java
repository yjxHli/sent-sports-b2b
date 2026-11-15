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
    public PageResult<OrderDtos.OrderListItem> listMyOrders(Long customerId, String status,
                                                            PageQuery pageQuery) {
        if (customerId == null) {
            return PageResult.of(List.of(), 0L, pageQuery.getPage(), pageQuery.getSize());
        }
        Page<OrderPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<OrderPO> rows = orderMapper.selectMyPage(page, customerId, status);

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
                                .map(OrderQueryServiceImpl::toItemLine)
                                .toList()))
                .toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    /** 行投影 → 前端商品行：金额一律「分 + 元」，行实付用于退款行上限。 */
    private static OrderDtos.OrderItemLine toItemLine(OrderItemListRow i) {
        return new OrderDtos.OrderItemLine(
                i.getSkuId() == null ? null : String.valueOf(i.getSkuId()),
                i.getProductId() == null ? null : String.valueOf(i.getProductId()),
                i.getTitle(), i.getSpec(), i.getQty(),
                i.getUnitPriceCent(),
                MoneyUtil.toYuan(i.getUnitPriceCent() == null ? 0L : i.getUnitPriceCent()),
                i.getLinePaidCent(),
                MoneyUtil.toYuan(i.getLinePaidCent() == null ? 0L : i.getLinePaidCent()));
    }

    /** 下单时间按系统时区转毫秒（前端统一按毫秒渲染，`JacksonConfig` 会把 Long 序列化成字符串）。 */
    private Long toMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Override
    public OrderDtos.OrderVO getDetail(String orderNo) {
        OrderPO o = orderMapper.selectByOrderNo(orderNo);
        if (o == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
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
        return new OrderDtos.OrderVO(
                o.getOrderNo(), o.getStatus(), o.getPayStatus(), null, o.getAfterSaleStatus(),
                o.getPayableCent(), MoneyUtil.toYuan(nonNull(o.getPayableCent())),
                o.getGoodsAmountCent(), MoneyUtil.toYuan(nonNull(o.getGoodsAmountCent())),
                o.getFreightCent(), MoneyUtil.toYuan(nonNull(o.getFreightCent())),
                o.getPointDeductCent(), MoneyUtil.toYuan(nonNull(o.getPointDeductCent())),
                itemRows.stream().map(OrderQueryServiceImpl::toItemLine).toList(),
                toMillis(o.getCreatedAt()),
                toMillis(o.getPaidAt()),
                toMillis(o.getCompletedAt()),
                o.getDiscountCent(),
                o.getCustomFeeCent(),
                o.getAddressSnapshotJson(),
                shipment == null ? null : shipment.getCarrier(),
                shipment == null ? null : shipment.getTrackingNo(),
                shipment == null ? null : toMillis(shipment.getShippedAt()),
                payment == null ? null : payment.channel(),
                payment == null ? null : payment.txnNo(),
                payment == null ? null : payment.tradeNo());
    }

    private long nonNull(Long value) {
        return value == null ? 0L : value;
    }
}
