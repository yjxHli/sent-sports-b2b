package com.sent.sports.order.controller;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.order.dto.OrderDtos;
import com.sent.sports.order.service.CancelOrderUseCase;
import com.sent.sports.order.service.ConfirmReceiptUseCase;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.dto.CancelOrderCmd;
import com.sent.sports.order.service.dto.ConfirmReceiptCmd;
import com.sent.sports.order.service.dto.ConfirmReceiptResult;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.order.service.dto.OrderLineCmd;
import com.sent.sports.order.service.dto.ReceiveLineCmd;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单控制器（P4-16）：下单 / 我的订单 / 详情 / 取消 / 确认收货（plan §8.5）。
 * <p>依赖同模块用例（{@link CreateOrderUseCase} / {@link CancelOrderUseCase} / {@link ConfirmReceiptUseCase}）
 * 与查询门面 {@link OrderQueryService}（桩，P5 接入仓储）。当前用户绑定（@CurrentUserArg）在 Phase 5，
 * 现以请求体携带的 customerId 占位。</p>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final ConfirmReceiptUseCase confirmReceiptUseCase;
    private final OrderQueryService orderQueryService;

    public OrderController(CreateOrderUseCase createOrderUseCase, CancelOrderUseCase cancelOrderUseCase,
                           ConfirmReceiptUseCase confirmReceiptUseCase, OrderQueryService orderQueryService) {
        this.createOrderUseCase = createOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.confirmReceiptUseCase = confirmReceiptUseCase;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    @RequirePerm("order:create")
    public R<OrderDtos.OrderVO> create(@Valid @RequestBody OrderDtos.CreateOrderCmd cmd) {
        CreateOrderResult r = createOrderUseCase.create(toCmd(cmd));
        return R.ok(toVo(r));
    }

    @GetMapping
    public R<PageResult<OrderDtos.OrderListItem>> list(
            @ModelAttribute PageQuery page,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        // 主体取自 AuthFilter 解析的 JWT sub，不再由前端请求体指定，避免越权查看他人订单
        Long customerId = user == null || user.isAdmin() ? null : user.userId();
        return R.ok(orderQueryService.listMyOrders(customerId, page));
    }

    @GetMapping("/{orderNo}")
    public R<OrderDtos.OrderVO> detail(@PathVariable String orderNo) {
        return R.ok(orderQueryService.getDetail(orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    @RequirePerm("order:cancel")
    public R<Void> cancel(@PathVariable String orderNo) {
        cancelOrderUseCase.cancel(new CancelOrderCmd(orderNo));
        return R.ok();
    }

    @PostMapping("/{orderNo}/confirm")
    @RequirePerm("order:confirm")
    public R<OrderDtos.OrderVO> confirm(@PathVariable String orderNo,
                                        @Valid @RequestBody OrderDtos.ConfirmCmd cmd) {
        ConfirmReceiptCmd c = new ConfirmReceiptCmd(orderNo,
                cmd.lines().stream().map(l -> new ReceiveLineCmd(l.skuId(), l.qty())).toList());
        ConfirmReceiptResult r = confirmReceiptUseCase.confirm(c);
        return R.ok(new OrderDtos.OrderVO(r.orderNo(), r.status(), null, null, null,
                null, null, null, null, null, null, null, null));
    }

    private CreateOrderCmd toCmd(OrderDtos.CreateOrderCmd cmd) {
        List<OrderLineCmd> lines = cmd.items().stream().map(i -> new OrderLineCmd(
                i.skuId(), i.qty(), i.customerTypeCode(), i.memberDiscountRate(),
                i.agreementDiscountRate(), i.allowStacking(), i.ladderTiers(),
                Boolean.TRUE.equals(i.customFlag()))).toList();
        return new CreateOrderCmd(cmd.customerId(), cmd.idemKey(), cmd.availablePoints(),
                cmd.freightFreeThresholdCent(), cmd.freightFeeCent(), lines);
    }

    private OrderDtos.OrderVO toVo(CreateOrderResult r) {
        return new OrderDtos.OrderVO(r.orderNo(), r.status(), null, null, null,
                r.payableCent(), MoneyUtil.toYuan(r.payableCent()),
                r.goodsAmountCent(), MoneyUtil.toYuan(r.goodsAmountCent()),
                r.freightCent(), MoneyUtil.toYuan(r.freightCent()),
                r.pointDeductCent(), MoneyUtil.toYuan(r.pointDeductCent()));
    }
}
