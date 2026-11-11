package com.sent.sports.refund.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.dto.AuditRefundCmd;
import com.sent.sports.refund.service.dto.AuditRefundResult;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 退款审核用例（P3-21）：审核通过触发回调、驳回必填原因、二次申诉限一次。
 * 仅待审核(PENDING)可批准/驳回；被驳回(CLOSED)可申诉一次回到 PENDING，二次申诉拒绝。
 * 审核通过经 RefundCallbackService 触发实际退款回调（P3-22/23 实现），本用例只负责决策与状态变更。
 */
@Service
public class RefundAuditUseCase {

    private static final int MAX_APPEAL = 1;

    private final RefundRepository refundRepository;
    private final RefundCallbackService refundCallbackService;

    public RefundAuditUseCase(RefundRepository refundRepository, RefundCallbackService refundCallbackService) {
        this.refundRepository = refundRepository;
        this.refundCallbackService = refundCallbackService;
    }

    @UseCase
    public AuditRefundResult audit(AuditRefundCmd cmd) {
        RefundOrder order = refundRepository.findByRefundNo(cmd.refundNo())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "退款单不存在"));
        switch (cmd.action()) {
            case "APPROVE" -> approve(order);
            case "REJECT" -> reject(order, cmd.reason());
            case "APPEAL" -> appeal(order);
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知的审核动作");
        }
        refundRepository.save(order);
        return new AuditRefundResult(cmd.refundNo(), order.getStatus().name());
    }

    private void approve(RefundOrder order) {
        if (order.getStatus() != RefundStatus.PENDING) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅待审核退款单可批准");
        }
        order.setStatus(RefundStatus.REFUNDING);
        refundCallbackService.process(order);
    }

    private void reject(RefundOrder order, String reason) {
        if (order.getStatus() != RefundStatus.PENDING) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅待审核退款单可驳回");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回必须填写原因");
        }
        order.setStatus(RefundStatus.CLOSED);
    }

    private void appeal(RefundOrder order) {
        if (order.getStatus() != RefundStatus.CLOSED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅被驳回的退款单可申诉");
        }
        if (order.getAppealCount() >= MAX_APPEAL) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "申诉次数已达上限");
        }
        order.setAppealCount(order.getAppealCount() + 1);
        order.setStatus(RefundStatus.PENDING);
    }
}
