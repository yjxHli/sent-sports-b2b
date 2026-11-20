package com.sent.sports.refund.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.refund.domain.RefundAction;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.dto.AuditRefundCmd;
import com.sent.sports.refund.service.dto.AuditRefundResult;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 退款审核用例（P3-21）：审核通过触发回调、驳回必填原因、二次申诉限一次。
 * 仅待审核(PENDING)可批准/驳回；被驳回(CLOSED)可申诉一次回到 PENDING，二次申诉拒绝。
 * 审核通过经 RefundCallbackService 触发实际退款回调（P3-22/23 实现），本用例只负责决策与状态变更。
 *
 * <p>驳回会投递站内信给下单客户（经 notify 模块 {@link MessageSendService}）：
 * 用户不盯着售后列表，被驳回却毫不知情是这轮反馈的核心痛点，因此红点必须亮。</p>
 */
@Service
public class RefundAuditUseCase {

    private static final int MAX_APPEAL = 1;

    private final RefundRepository refundRepository;
    private final RefundCallbackService refundCallbackService;
    private final OrderRefundViewService orderRefundViewService;
    private final MessageSendService messageSendService;
    /** 动作 → 差异副作用；状态变更本身统一由各 handler 内的 order.transit 完成。 */
    private final Map<RefundAction, AuditHandler> handlers;

    @FunctionalInterface
    private interface AuditHandler {
        void handle(RefundOrder order, String reason);
    }

    public RefundAuditUseCase(RefundRepository refundRepository,
                              RefundCallbackService refundCallbackService,
                              OrderRefundViewService orderRefundViewService,
                              MessageSendService messageSendService) {
        this.refundRepository = refundRepository;
        this.refundCallbackService = refundCallbackService;
        this.orderRefundViewService = orderRefundViewService;
        this.messageSendService = messageSendService;
        this.handlers = Map.of(
                RefundAction.APPROVE, (order, reason) -> approve(order),
                RefundAction.REJECT, this::reject,
                RefundAction.APPEAL, (order, reason) -> appeal(order));
    }

    @UseCase
    public AuditRefundResult audit(AuditRefundCmd cmd) {
        RefundOrder order = refundRepository.findByRefundNo(cmd.refundNo())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "退款单不存在"));
        RefundAction action = RefundAction.of(cmd.action());
        AuditHandler handler = handlers.get(action);
        if (handler == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知的审核动作");
        }
        handler.handle(order, cmd.reason());
        refundRepository.save(order);
        return new AuditRefundResult(cmd.refundNo(), order.getStatus().name());
    }

    private void approve(RefundOrder order) {
        order.transit(RefundAction.APPROVE); // 仅 PENDING → REFUNDING，其他状态统一 40901
        refundCallbackService.process(order);
    }

    private void reject(RefundOrder order, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回必须填写原因");
        }
        order.transit(RefundAction.REJECT); // 仅 PENDING → CLOSED

        // 驳回原因必须留痕：管理端列表的 reason 列取 COALESCE(audit_remark, remark, reason_code)，
        // 不写这一列的话客服复核/用户申诉时只能看到当初的申请理由，看不到为什么被驳回。
        order.setAuditRemark(reason.trim());
        notifyRejected(order, reason.trim());
    }

    /**
     * 驳回通知：站内信里的「为什么被驳回」比状态本身更重要，故把审核原因原样带上。
     * <p>订单查不到（历史脏数据 / 已物理清理）时只跳过通知，不能让审核动作整体失败——
     * 退款单关不掉的代价远大于少一条通知。</p>
     */
    private void notifyRejected(RefundOrder order, String reason) {
        Optional<Long> customerId = orderRefundViewService.getView(order.getOrderNo())
                .map(view -> view.customerId())
                .filter(id -> id > 0L);
        if (customerId.isEmpty()) {
            return;
        }
        String content = "退款单 " + order.getRefundNo() + "（订单 " + order.getOrderNo() + "）未通过审核。"
                + "驳回原因：" + reason + "。如需补充凭证，可在「我的售后」中申诉一次。";
        messageSendService.send(new InboxMessage(customerId.get(), "ORDER", "退款申请被驳回",
                content, "REFUND", order.getRefundNo()));
    }

    private void appeal(RefundOrder order) {
        if (!order.canTransit(RefundAction.APPEAL)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅被驳回的退款单可申诉");
        }
        if (order.getAppealCount() >= MAX_APPEAL) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "申诉次数已达上限");
        }
        order.setAppealCount(order.getAppealCount() + 1);
        order.transit(RefundAction.APPEAL); // CLOSED → PENDING
    }
}
