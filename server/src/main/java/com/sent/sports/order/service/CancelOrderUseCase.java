package com.sent.sports.order.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderAction;
import com.sent.sports.order.domain.OrderItem;
import com.sent.sports.order.domain.OrderStateMachine;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.dto.CancelOrderCmd;
import com.sent.sports.order.service.dto.CancelOrderResult;
import com.sent.sports.stock.service.StockService;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 取消订单用例（P3-09）：仅待付款（WAIT_PAY）可取消，释放预占库存并置为 CANCELLED。
 * 已支付 / 已取消 / 其他状态迁移非法，抛 STATE_CONFLICT(40901)。
 * 依赖 order 仓储端口与 stock 服务接口，不直连其他模块仓储或领域类型（ArchUnit 规则 4/6）。
 */
@Service
public class CancelOrderUseCase {

    private final OrderRepository orderRepository;
    private final StockService stockService;

    public CancelOrderUseCase(OrderRepository orderRepository, StockService stockService) {
        this.orderRepository = orderRepository;
        this.stockService = stockService;
    }

    @UseCase
    public CancelOrderResult cancel(CancelOrderCmd cmd) {
        return cancel(cmd, null);
    }

    /**
     * 取消订单（带归属校验，2026-11-28 批次 1 / P0-02）。
     *
     * @param viewerCustomerId 发起人客户号；{@code null} 表示系统任务/管理端（不校验归属）。
     *                         非空且与订单归属不一致抛 {@code FORBIDDEN(40301)}，
     *                         校验放在状态机与库存释放**之前**：越权请求不该产生任何副作用。
     */
    @UseCase
    public CancelOrderResult cancel(CancelOrderCmd cmd, Long viewerCustomerId) {
        Order order = orderRepository.findByOrderNo(cmd.orderNo())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单不存在"));

        if (viewerCustomerId != null && !order.isOwnedBy(viewerCustomerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权取消该订单");
        }

        // 仅 WAIT_PAY 允许 USER_CANCEL；其余状态（含已支付 WAIT_SHIP、已取消）一律拒绝
        if (!OrderStateMachine.canTransit(order.getStatus(), OrderAction.USER_CANCEL)) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }

        // 待付款整单未发货，按行全量释放预占库存
        for (OrderItem item : order.getItems()) {
            stockService.release(item.getSkuId(), item.getQty());
        }

        order.transit(OrderAction.USER_CANCEL); // WAIT_PAY -> CANCELLED
        orderRepository.save(order);

        return new CancelOrderResult(order.getOrderNo(), order.getStatus().name());
    }
}
