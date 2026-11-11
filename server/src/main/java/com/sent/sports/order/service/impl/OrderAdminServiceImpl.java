package com.sent.sports.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.order.dto.admin.AdminOrderDtos;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderDetailLineRow;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.service.CloseOrderUseCase;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.ShipOrderUseCase;
import com.sent.sports.order.service.admin.OrderAdminService;
import com.sent.sports.order.service.dto.CloseOrderCmd;
import com.sent.sports.order.service.dto.CloseOrderResult;
import com.sent.sports.order.service.dto.ShipOrderCmd;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 订单管理（管理端）：列表/详情走真库，发货委托 {@link ShipOrderUseCase}，
 * 关单委托 {@link CloseOrderUseCase} 完成状态机、退款与补偿闭环。
 * <p>代客下单/拆单仍是 P4 占位（不在本次解除范围）。</p>
 */
@Service
public class OrderAdminServiceImpl implements OrderAdminService {

    private final ShipOrderUseCase shipOrderUseCase;
    private final OrderQueryService orderQueryService;
    private final OrderMapper orderMapper;
    private final CloseOrderUseCase closeOrderUseCase;

    public OrderAdminServiceImpl(ShipOrderUseCase shipOrderUseCase, OrderQueryService orderQueryService,
                                 OrderMapper orderMapper, CloseOrderUseCase closeOrderUseCase) {
        this.shipOrderUseCase = shipOrderUseCase;
        this.orderQueryService = orderQueryService;
        this.orderMapper = orderMapper;
        this.closeOrderUseCase = closeOrderUseCase;
    }

    @Override
    public AdminOrderDtos.AdminOrderVO agentCreate(AdminOrderDtos.AgentCreateCmd cmd) {
        return new AdminOrderDtos.AdminOrderVO("stub-O", "WAIT_PAY", 0L, "0.00");
    }

    @Override
    public AdminOrderDtos.AdminOrderVO split(AdminOrderDtos.SplitCmd cmd) {
        return new AdminOrderDtos.AdminOrderVO("stub-split", "SPLIT", 0L, "0.00");
    }

    @Override
    public com.sent.sports.order.service.dto.ShipOrderResult ship(String orderNo, AdminOrderDtos.ShipCmd cmd) {
        ShipOrderCmd shipCmd = new ShipOrderCmd(orderNo, cmd.carrier(), cmd.trackingNo(), cmd.lines());
        return shipOrderUseCase.ship(shipCmd);
    }

    @Override
    @UseCase
    public AdminOrderDtos.AdminOrderVO closeOrder(String orderNo, String reason) {
        // 操作人从 Web 请求上下文取（AuthFilter 注入 CurrentUser）；Job / 单测无上下文时为 null，
        // sys_operation_log.staff_id 允许为空。这样既保持 closeOrder(orderNo, reason) 的简洁契约，
        // 又能把「谁关的单」留痕，不需要让 controller 关心审计字段。
        CloseOrderResult result = closeOrderUseCase.close(
                new CloseOrderCmd(orderNo, reason, currentOperatorId()));
        return new AdminOrderDtos.AdminOrderVO(result.orderNo(), result.status(),
                result.payableCent(), MoneyUtil.toYuan(result.payableCent()));
    }

    private Long currentOperatorId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return null;
        }
        Object user = servlet.getRequest().getAttribute(CurrentUser.ATTR);
        return user instanceof CurrentUser currentUser ? currentUser.userId() : null;
    }

    @Override
    public PageResult<AdminOrderDtos.AdminOrderVO> list(PageQuery page, String status) {
        Page<OrderPO> query = new Page<>(page.getPage(), page.getSize());
        IPage<OrderPO> rows = orderMapper.selectAllPage(query, normalizeStatus(status));
        List<AdminOrderDtos.AdminOrderVO> list = rows.getRecords().stream().map(o -> {
            long cent = o.getPayableCent() == null ? 0L : o.getPayableCent();
            return new AdminOrderDtos.AdminOrderVO(
                    o.getOrderNo(), o.getStatus(), cent, MoneyUtil.toYuan(cent));
        }).toList();
        return PageResult.of(list, rows.getTotal(), page.getPage(), page.getSize());
    }

    /** 空 / 全空白的 status 视为「不过滤」，避免 clearable 下拉把空串传进 SQL。 */
    private String normalizeStatus(String status) {
        return (status == null || status.isBlank()) ? null : status.trim();
    }

    @Override
    public AdminOrderDtos.AdminOrderDetailVO detail(String orderNo) {
        // P4 这里曾无条件 throw NOT_FOUND，导致管理端「订单详情」页每次都弹 404。
        // 现在按订单号读主表 + 一次联查读订单行，查不到才按契约抛 NOT_FOUND。
        OrderPO o = orderMapper.selectByOrderNo(orderNo);
        if (o == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        List<OrderDetailLineRow> rows = o.getId() == null
                ? List.of()
                : orderMapper.selectDetailLines(o.getId());
        List<AdminOrderDtos.AdminOrderLineVO> lines = rows.stream()
                .map(r -> new AdminOrderDtos.AdminOrderLineVO(
                        r.getSkuId(), r.getSkuName(), r.getSpec(), r.getQty(),
                        r.getUnitPriceCent(), r.getShippedQty(), r.getCustomStatus()))
                .toList();
        return new AdminOrderDtos.AdminOrderDetailVO(
                o.getOrderNo(), o.getStatus(), o.getPayStatus(), shipStatusOf(o.getStatus()),
                o.getAfterSaleStatus(), o.getCustomerId(), toMillis(o.getCreatedAt()),
                nonNull(o.getGoodsAmountCent()), nonNull(o.getFreightCent()),
                nonNull(o.getPointDeductCent()), nonNull(o.getPayableCent()),
                nonNull(o.getPaidCent()), lines);
    }

    /** orders 表没有独立的 ship_status 列，按主状态推导成前端 StatusTag 认识的枚举值。 */
    private String shipStatusOf(String status) {
        if (status == null) {
            return "WAIT_SHIP";
        }
        return switch (status) {
            case "PART_SHIPPED" -> "PART_SHIPPED";
            case "WAIT_RECEIVE", "COMPLETED" -> "SHIPPED";
            case "CANCELLED", "CLOSED" -> "CLOSED";
            default -> "WAIT_SHIP";
        };
    }

    private Long toMillis(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long nonNull(Long value) {
        return value == null ? 0L : value;
    }
}
