package com.sent.sports.order.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.service.ConfirmTokenService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单管理控制器：多维筛选、代客下单、拆单、发货、关单。
 * <p>关单是危险操作：{@code @RequirePerm("order:close")} 由权限拦截器校验，
 * {@code X-Confirm-Token} 用后即焚（短语「确认关闭」在换令牌时由 ConfirmAction 校验）。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {

    private static final String CLOSE_ACTION = "ORDER_CLOSE";

    private final OrderAdminService orderAdminService;
    private final ConfirmTokenService confirmTokenService;

    public AdminOrderController(OrderAdminService orderAdminService,
                                ConfirmTokenService confirmTokenService) {
        this.orderAdminService = orderAdminService;
        this.confirmTokenService = confirmTokenService;
    }

    @GetMapping("/orders")
    public R<PageResult<AdminOrderDtos.AdminOrderVO>> list(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) String status) {
        return R.ok(orderAdminService.list(page, status));
    }

    @GetMapping("/orders/{id}")
    public R<AdminOrderDtos.AdminOrderDetailVO> detail(@PathVariable String id) {
        return R.ok(orderAdminService.detail(id));
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
    public R<ShipOrderResult> ship(@PathVariable String id, @Valid @RequestBody AdminOrderDtos.ShipCmd cmd) {
        return R.ok(orderAdminService.ship(id, cmd));
    }

    /**
     * 关闭订单：幂等由订单状态条件更新保证；令牌以 action|orderNo 绑定，用后即焚。
     * 关单原因由 {@code CloseCmd.reason} 的 @NotBlank 校验，并写入操作日志与退款单备注。
     */
    @PostMapping("/orders/{id}/close")
    @RequirePerm("order:close")
    public R<AdminOrderDtos.AdminOrderVO> close(
            @PathVariable("id") String orderNo,
            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken,
            @Valid @RequestBody AdminOrderDtos.CloseCmd cmd) {
        if (confirmToken == null || confirmToken.isBlank()
                || !confirmTokenService.consume(CLOSE_ACTION, orderNo, confirmToken)) {
            throw new BizException(ErrorCode.CONFIRM_TOKEN_INVALID);
        }
        return R.ok(orderAdminService.closeOrder(orderNo, cmd.reason()));
    }
}
