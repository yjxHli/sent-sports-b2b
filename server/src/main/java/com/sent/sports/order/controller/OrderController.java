package com.sent.sports.order.controller;

import com.sent.sports.common.anno.Idempotent;
import com.sent.sports.common.anno.RateLimit;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.dto.admin.BatchDeleteDtos;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.order.dto.OrderDtos;
import com.sent.sports.order.dto.OrderVOAssembler;
import com.sent.sports.order.service.BatchDeleteOrdersUseCase;
import com.sent.sports.order.service.CancelOrderUseCase;
import com.sent.sports.order.service.ConfirmReceiptUseCase;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.DeleteOrderUseCase;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.dto.CancelOrderCmd;
import com.sent.sports.order.service.dto.ConfirmReceiptCmd;
import com.sent.sports.order.service.dto.ConfirmReceiptResult;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.order.service.dto.OrderLineCmd;
import com.sent.sports.order.service.dto.ReceiveLineCmd;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final DeleteOrderUseCase deleteOrderUseCase;
    private final BatchDeleteOrdersUseCase batchDeleteOrdersUseCase;
    private final OrderQueryService orderQueryService;

    public OrderController(CreateOrderUseCase createOrderUseCase, CancelOrderUseCase cancelOrderUseCase,
                           ConfirmReceiptUseCase confirmReceiptUseCase, DeleteOrderUseCase deleteOrderUseCase,
                           BatchDeleteOrdersUseCase batchDeleteOrdersUseCase,
                           OrderQueryService orderQueryService) {
        this.createOrderUseCase = createOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.confirmReceiptUseCase = confirmReceiptUseCase;
        this.deleteOrderUseCase = deleteOrderUseCase;
        this.batchDeleteOrdersUseCase = batchDeleteOrdersUseCase;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    @RequirePerm("order:create")
    @Idempotent(scene = "ORDER_CREATE")
    @RateLimit(limit = 30, windowSeconds = 60)
    public R<OrderDtos.OrderVO> create(@Valid @RequestBody OrderDtos.CreateOrderCmd cmd) {
        CreateOrderResult r = createOrderUseCase.create(toCmd(cmd));
        return R.ok(toVo(r));
    }

    @GetMapping
    public R<PageResult<OrderDtos.OrderListItem>> list(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        // 主体取自 AuthFilter 解析的 JWT sub，不再由前端请求体指定，避免越权查看他人订单
        Long customerId = user == null || user.isAdmin() ? null : user.userId();
        return R.ok(orderQueryService.listMyOrders(customerId, status, keyword, page));
    }

    @GetMapping("/{orderNo}")
    @RequirePerm("order:read")
    public R<OrderDtos.OrderVO> detail(@PathVariable String orderNo,
                                      @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        // 2026-11-28 批次 1 / P0-02：主体来自令牌，客户只可能读到自己的单（管理端令牌传 null 跳过归属校验）
        Long viewerCustomerId = user == null || user.isAdmin() ? null : user.userId();
        return R.ok(orderQueryService.getDetail(orderNo, viewerCustomerId));
    }

    @PostMapping("/{orderNo}/cancel")
    @RequirePerm("order:cancel")
    public R<Void> cancel(@PathVariable String orderNo,
                          @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        Long viewerCustomerId = user == null || user.isAdmin() ? null : user.userId();
        cancelOrderUseCase.cancel(new CancelOrderCmd(orderNo), viewerCustomerId);
        return R.ok();
    }

    @PostMapping("/{orderNo}/confirm")
    @RequirePerm("order:confirm")
    public R<OrderDtos.OrderVO> confirm(@PathVariable String orderNo,
                                        @Valid @RequestBody OrderDtos.ConfirmCmd cmd,
                                        @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        ConfirmReceiptCmd c = new ConfirmReceiptCmd(orderNo,
                cmd.lines().stream().map(l -> new ReceiveLineCmd(l.skuId(), l.qty())).toList());
        Long viewerCustomerId = user == null || user.isAdmin() ? null : user.userId();
        ConfirmReceiptResult r = confirmReceiptUseCase.confirm(c, viewerCustomerId);
        return R.ok(OrderVOAssembler.builder()
                .orderNo(r.orderNo())
                .status(r.status())
                .build());
    }

    /**
     * 删除订单（淘宝式详情页「删除订单」）：客户侧软删，只从「我的订单」里移除。
     * <p>管理端令牌不在此列——管理员要清理订单走管理端接口，避免误删客户视图。</p>
     */
    @DeleteMapping("/{orderNo}")
    @RequirePerm("order:delete")
    public R<Void> delete(@PathVariable String orderNo,
                          @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        deleteOrderUseCase.delete(orderNo, user == null || user.isAdmin() ? null : user.userId());
        return R.ok();
    }

    /**
     * 我的订单「多选删除」：{@code POST /api/orders/batch-delete}，体 {@code {"ids":["SO..."]}}。
     *
     * <p>与单条删除同一套权限点（{@code order:delete}），但**不给整批一个失败码**：
     * 多选里只要有一单在途，就把能删的删掉、删不掉的逐条回原因，前端提示「已删除 X 单」。
     * 路径写在校验之前不会与 {@code /{orderNo}} 冲突（后者是 GET/DELETE，这里是 POST）。</p>
     */
    @PostMapping("/batch-delete")
    @RequirePerm("order:delete")
    public R<OrderDtos.BatchDeleteResult> batchDelete(
            @Valid @RequestBody BatchDeleteDtos.BatchDeleteCmd cmd,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        var result = batchDeleteOrdersUseCase.delete(
                cmd.ids(), user == null || user.isAdmin() ? null : user.userId());
        return R.ok(new OrderDtos.BatchDeleteResult(result.deleted(),
                result.skipped().stream()
                        .map(s -> new OrderDtos.SkippedItem(s.orderNo(), s.reason()))
                        .toList()));
    }

    private CreateOrderCmd toCmd(OrderDtos.CreateOrderCmd cmd) {
        List<OrderLineCmd> lines = cmd.items().stream().map(i -> new OrderLineCmd(
                i.skuId(), i.qty(), i.customerTypeCode(), i.memberDiscountRate(),
                i.agreementDiscountRate(), i.allowStacking(), i.ladderTiers(),
                Boolean.TRUE.equals(i.customFlag()))).toList();
        return new CreateOrderCmd(cmd.customerId(), cmd.addressId(), cmd.idemKey(), cmd.availablePoints(),
                cmd.freightFreeThresholdCent(), cmd.freightFeeCent(), lines, cmd.couponId());
    }

    private OrderDtos.OrderVO toVo(CreateOrderResult r) {
        return OrderVOAssembler.builder()
                .orderNo(r.orderNo())
                .status(r.status())
                .payable(r.payableCent())
                .goodsAmount(r.goodsAmountCent())
                .freight(r.freightCent())
                .pointDeduct(r.pointDeductCent())
                .build();
    }
}
