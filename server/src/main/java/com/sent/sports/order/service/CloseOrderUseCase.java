package com.sent.sports.order.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.service.CreditFacade;
import com.sent.sports.order.domain.OrderAction;
import com.sent.sports.order.domain.OrderStateMachine;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.domain.PayAction;
import com.sent.sports.order.domain.PayStateMachine;
import com.sent.sports.order.domain.PayStatus;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.service.dto.CloseOrderCmd;
import com.sent.sports.order.service.dto.CloseOrderResult;
import com.sent.sports.refund.service.RefundService;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.system.service.SystemService;
import org.springframework.stereotype.Service;

/**
 * 管理端关闭订单用例：状态机校验 → 条件迁移 → 全额退款 → 库存/授信补偿 → 操作日志。
 *
 * <p><b>业务口径</b>：只有 WAIT_SHIP（整单未发）与 PART_SHIPPED（部分发货）可由管理员关单；
 * WAIT_RECEIVE 及以上必须走退货/售后流程。关单一律全额退款（订单支付维度按
 * {@link PayStateMachine} 从 PAID 迁到 FULL_REFUNDED，支付流水维度由 payment 模块置 REFUNDED）。</p>
 *
 * <p><b>一致性</b>：{@link UseCase} 由 {@code UseCaseAspect} 的 TransactionTemplate 包裹，
 * 任一步抛异常（含模拟退款网关拒绝）全部 DB 写入一起回滚；
 * 并发重复关单靠 {@code closeOnAdminRefund} 的「旧状态 + 旧支付状态」条件更新挡住。</p>
 *
 * <p><b>模块边界</b>：跨模块只依赖对方的 service 接口（refund / finance / system）与仓储端口
 * （stock），不碰对方 mapper / domain / controller，满足 ArchUnit 规则 4。</p>
 */
@Service
public class CloseOrderUseCase {

    private final OrderMapper orderMapper;
    private final StockRepository stockRepository;
    private final RefundService refundService;
    private final CreditFacade creditFacade;
    private final SystemService systemService;

    public CloseOrderUseCase(OrderMapper orderMapper,
                             StockRepository stockRepository,
                             RefundService refundService,
                             CreditFacade creditFacade,
                             SystemService systemService) {
        this.orderMapper = orderMapper;
        this.stockRepository = stockRepository;
        this.refundService = refundService;
        this.creditFacade = creditFacade;
        this.systemService = systemService;
    }

    @UseCase
    public CloseOrderResult close(CloseOrderCmd cmd) {
        String orderNo = cmd.orderNo();
        String reason = cmd.reason();
        if (orderNo == null || orderNo.isBlank() || reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }

        OrderPO po = orderMapper.selectByOrderNo(orderNo);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (po.getId() == null) {
            // 退款单 order_id 必须是真主键；历史脏数据缺主键时宁可拒绝也不写坏外键。
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }

        OrderStatus status = parseStatus(po.getStatus());
        if (!OrderStateMachine.canTransit(status, OrderAction.ADMIN_CLOSE)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅待发货/部分发货订单可关单，已全发请走退货流程");
        }
        PayStatus payStatus = parsePayStatus(po.getPayStatus());
        if (!PayStateMachine.canTransit(payStatus, PayAction.FULL_REFUND)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "当前支付状态不允许全额退款");
        }
        PayStatus targetPayStatus = PayStateMachine.transit(payStatus, PayAction.FULL_REFUND);

        long payableCent = nonNull(po.getPayableCent());
        // 实付优先；演示/兼容数据可能只标了 PAID 未写 paid_cent，则以应付兜底。
        long refundedCent = nonNull(po.getPaidCent()) > 0 ? nonNull(po.getPaidCent()) : payableCent;
        if (refundedCent <= 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "订单无可退金额");
        }

        // 条件更新：并发下第二个关单请求必然 0 行，直接拒绝，杜绝二次退款/二次释放库存。
        if (orderMapper.closeOnAdminRefund(orderNo, status.name(), targetPayStatus.name(),
                payStatus.name()) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }

        String refundNo = refundService.createCloseRefund(orderNo, po.getId(), refundedCent, reason);

        boolean stockReleased = status == OrderStatus.WAIT_SHIP;
        if (stockReleased) {
            // uk_flow_biz 唯一索引保证幂等；重复释放不会减两次 locked_qty。
            stockRepository.releaseByOrderNo(orderNo);
        }
        // 授信只在「确认收货→生成应收」时占用，门面内部按应收记录判断是否需要回冲。
        creditFacade.releaseCreditForOrder(orderNo);
        systemService.recordLog("ORDER", "CLOSE", "ORDER", orderNo, reason, cmd.operatorId());

        return new CloseOrderResult(orderNo, OrderStatus.CLOSED.name(), refundNo,
                refundedCent, payableCent, stockReleased);
    }

    private OrderStatus parseStatus(String value) {
        try {
            return OrderStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
    }

    private PayStatus parsePayStatus(String value) {
        try {
            return PayStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
    }

    private long nonNull(Long value) {
        return value == null ? 0L : value;
    }
}
