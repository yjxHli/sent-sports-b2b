package com.sent.sports.order.controller.admin;

import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.anno.Idempotent;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.order.dto.admin.AdminOrderDtos;
import com.sent.sports.order.service.admin.OrderAdminService;
import com.sent.sports.order.service.dto.ShipOrderResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单管理控制器：多维筛选、代客下单、拆单、发货、关单。
 * <p>关单是危险操作：{@code @RequirePerm("order:close")} 由权限拦截器校验，
 * {@code @Confirm(ORDER_CLOSE)} 由确认令牌拦截器校验并用后即焚（短语「确认关闭」在换令牌时校验）。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {

    private final OrderAdminService orderAdminService;

    public AdminOrderController(OrderAdminService orderAdminService) {
        this.orderAdminService = orderAdminService;
    }

    @GetMapping("/orders")
    @RequirePerm("order:view")
    public R<PageResult<AdminOrderDtos.AdminOrderVO>> list(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(orderAdminService.list(page, status, keyword));
    }

    @GetMapping("/orders/{id}")
    @RequirePerm("order:view")
    public R<AdminOrderDtos.AdminOrderDetailVO> detail(@PathVariable String id) {
        return R.ok(orderAdminService.detail(id));
    }

    /**
     * 某位顾客的最近订单（客服工作台右侧「顾客历史订单」侧栏）。
     * <p>客服接待时需要随手确认「这位顾客之前买过什么」，单独开一个轻量接口：
     * 走订单列表那套分页会让侧栏拿不到最新一页的语义，而这里只要「最近几笔」。</p>
     */
    @GetMapping("/customers/{customerId}/orders")
    @RequirePerm("order:view")
    public R<List<AdminOrderDtos.AdminCustomerOrderVO>> customerOrders(
            @PathVariable long customerId,
            @RequestParam(defaultValue = "10") int limit) {
        return R.ok(orderAdminService.customerOrders(customerId, limit));
    }

    @PostMapping("/orders")
    @RequirePerm("order:agent-create")
    public R<AdminOrderDtos.AdminOrderVO> agentCreate(@Valid @RequestBody AdminOrderDtos.AgentCreateCmd cmd) {
        return R.ok(orderAdminService.agentCreate(cmd));
    }

    @PostMapping("/orders/{id}/split")
    @RequirePerm("order:split")
    public R<AdminOrderDtos.AdminOrderVO> split(@PathVariable Long id,
                                                @Valid @RequestBody AdminOrderDtos.SplitCmd cmd) {
        return R.ok(orderAdminService.split(cmd));
    }

    @PostMapping("/orders/{id}/ship")
    @RequirePerm("order:ship")
    @Idempotent(scene = "ORDER_SHIP")
    public R<ShipOrderResult> ship(@PathVariable String id, @Valid @RequestBody AdminOrderDtos.ShipCmd cmd) {
        return R.ok(orderAdminService.ship(id, cmd));
    }

    /**
     * 关闭订单：幂等由订单状态条件更新保证；令牌以 action|订单号 绑定，用后即焚（由
     * {@code ConfirmTokenInterceptor} 统一校验，目标取路径变量 {@code id}）。
     * 关单原因由 {@code CloseCmd.reason} 的 @NotBlank 校验，并写入操作日志与退款单备注。
     */
    @PostMapping("/orders/{id}/close")
    @Confirm(action = ConfirmAction.ORDER_CLOSE, target = Confirm.Target.PATH_VARIABLE, field = "id")
    @RequirePerm("order:close")
    public R<AdminOrderDtos.AdminOrderVO> close(
            @PathVariable("id") String orderNo,
            @Valid @RequestBody AdminOrderDtos.CloseCmd cmd) {
        return R.ok(orderAdminService.closeOrder(orderNo, cmd.reason()));
    }
}
