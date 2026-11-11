package com.sent.sports.order.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.service.OrderSettlementService;
import com.sent.sports.member.service.MemberRewardService;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderItem;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.dto.ConfirmReceiptCmd;
import com.sent.sports.order.service.dto.ConfirmReceiptResult;
import com.sent.sports.order.service.dto.ReceiveLineCmd;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 确认收货用例（P3-17）：plan 验收项——逐包裹确认、全部确认才完成、完成后发放成长值与积分。
 * 仅待收货(WAIT_RECEIVE)可确认；逐行累加已收；全部收齐 → COMPLETED 并经会员门面发奖励；
 * 部分确认保持 WAIT_RECEIVE。经 member 模块的 MemberRewardService 门面发放奖励，不直接依赖 member 领域类型（ArchUnit 规则 4）。
 */
@Service
public class ConfirmReceiptUseCase {

    private static final List<OrderStatus> RECEIVABLE = List.of(OrderStatus.WAIT_RECEIVE);

    private final OrderRepository orderRepository;
    private final MemberRewardService memberRewardService;
    private final OrderSettlementService orderSettlementService;

    public ConfirmReceiptUseCase(OrderRepository orderRepository, MemberRewardService memberRewardService,
                                 OrderSettlementService orderSettlementService) {
        this.orderRepository = orderRepository;
        this.memberRewardService = memberRewardService;
        this.orderSettlementService = orderSettlementService;
    }

    @UseCase
    public ConfirmReceiptResult confirm(ConfirmReceiptCmd cmd) {
        Order order = orderRepository.findByOrderNo(cmd.orderNo())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单不存在"));
        if (!RECEIVABLE.contains(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "订单当前状态不可确认收货");
        }

        if (cmd.lines().isEmpty()) {
            // 用户端订单列表只拿得到订单号，「确认收货」按钮不带明细：
            // 默认按「已发未收」逐行全量确认，避免状态永远停在 WAIT_RECEIVE。
            for (OrderItem item : order.getItems()) {
                int remaining = item.getShippedQty() - item.getReceivedQty();
                if (remaining > 0) {
                    item.receive(remaining);
                }
            }
        } else {
            for (ReceiveLineCmd line : cmd.lines()) {
                OrderItem item = order.getItems().stream()
                        .filter(i -> i.getSkuId() == line.skuId())
                        .findFirst()
                        .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单行不存在"));
                item.receive(line.qty()); // 校验数量>0 与 超收
            }
        }

        boolean allReceived = order.getItems().stream().allMatch(OrderItem::isFullyReceived);
        if (allReceived) {
            order.setStatus(OrderStatus.COMPLETED);
            // 成长值流水 / 客户余额经 member → customer 门面真实落账
            memberRewardService.grantOrderCompleted(order.getCustomerId(),
                    order.getOrderNo(), order.getPayableCent());
            // 应收台账 + 企业授信占用经 finance 门面真实落账（uk_receivable_order 幂等）
            orderSettlementService.onOrderCompleted(order.getOrderNo(),
                    order.getCustomerId(), order.getPayableCent());
        }
        orderRepository.save(order);

        return new ConfirmReceiptResult(cmd.orderNo(), order.getStatus().name(), allReceived);
    }
}
