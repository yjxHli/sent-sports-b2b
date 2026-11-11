package com.sent.sports.order.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.customize.service.CustomizeShipGateway;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderItem;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.domain.ShipmentProgress;
import com.sent.sports.order.event.ShipEvent;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.dto.ShipLineCmd;
import com.sent.sports.order.service.dto.ShipOrderCmd;
import com.sent.sports.order.service.dto.ShipOrderResult;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
/**
 * 发货用例（P3-15）：实现 plan §7.4 与验收项。
 * 流程：加载订单 → 仅待发货 / 部分发货可发 → 逐行校验(存在性 / 数量) →
 * 定制未完工拒绝 → 累加已发数量 → 状态重算(ShipmentProgress) → 落库 → 发布 ShipEvent。
 * 经 customize 模块的 CustomizeShipGateway 门面判定定制完工，不直接依赖 customize 领域类型（ArchUnit 规则 4）。
 */
@Service
public class ShipOrderUseCase {

    private static final List<OrderStatus> SHIPPABLE = List.of(OrderStatus.WAIT_SHIP, OrderStatus.PART_SHIPPED);

    private final OrderRepository orderRepository;
    private final CustomizeShipGateway customizeShipGateway;
    private final ShipmentRecorder shipmentRecorder;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ShipOrderUseCase(OrderRepository orderRepository, CustomizeShipGateway customizeShipGateway,
                            ShipmentRecorder shipmentRecorder, DomainEventPublisher eventPublisher,
                            Clock clock) {
        this.orderRepository = orderRepository;
        this.customizeShipGateway = customizeShipGateway;
        this.shipmentRecorder = shipmentRecorder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @UseCase
    public ShipOrderResult ship(ShipOrderCmd cmd) {
        Order order = orderRepository.findByOrderNo(cmd.orderNo())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单不存在"));
        if (!SHIPPABLE.contains(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "订单当前状态不可发货");
        }

        for (ShipLineCmd line : cmd.lines()) {
            OrderItem item = order.getItems().stream()
                    .filter(i -> i.getSkuId() == line.skuId())
                    .findFirst()
                    .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单行不存在"));
            // 定制未完工拒绝：经门面判定，非定制记录默认可发
            if (!customizeShipGateway.isReadyToShip(cmd.orderNo(), line.skuId())) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "定制未完工，不可发货");
            }
            item.ship(line.qty()); // 内部校验 数量>0 与 超发
        }

        int totalQty = order.getItems().stream().mapToInt(OrderItem::getQty).sum();
        int shippedQty = order.getItems().stream().mapToInt(OrderItem::getShippedQty).sum();
        order.setStatus(ShipmentProgress.recalc(totalQty, shippedQty));
        orderRepository.save(order);

        // 发货落账（与上面的聚合回写同处 @UseCase 事务）：
        // 1) 含定制标记的订单行生成 custom_order（uk_custom_order_item 幂等）
        // 2) 本次发货生成一个 shipment 包裹 + 逐行 shipment_item
        customizeShipGateway.createCustomOrdersForShipment(cmd.orderNo());
        shipmentRecorder.record(cmd.orderNo(), cmd.carrier(), cmd.trackingNo(),
                cmd.lines().stream()
                        .map(l -> new ShipmentRecorder.Line(l.skuId(), l.qty()))
                        .toList());

        eventPublisher.publish(new ShipEvent(cmd.orderNo(), cmd.carrier(), cmd.trackingNo(), clock.instant().toEpochMilli()));
        return new ShipOrderResult(cmd.orderNo(), order.getStatus().name());
    }
}
