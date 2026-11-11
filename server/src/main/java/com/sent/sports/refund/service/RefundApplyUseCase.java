package com.sent.sports.refund.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.customize.service.CustomizeRefundGateway;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.order.service.dto.RefundLineView;
import com.sent.sports.refund.domain.RefundCalculator;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.dto.RefundApplyCmd;
import com.sent.sports.refund.service.dto.RefundApplyLineCmd;
import com.sent.sports.refund.service.dto.RefundApplyResult;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 申请退款用例（P3-19）：plan §7.6 与验收项——凭证必填、定制已排产不可退款、超退拒绝、未知行/订单不存在、生成待审核退款单。
 * 经 order 模块 OrderRefundViewService 门面读取退款项（不直连 order.domain），
 * 经 customize 模块 CustomizeRefundGateway 门面判定定制是否进入生产（不直接依赖 customize.domain，ArchUnit 规则 4）。
 */
@Service
public class RefundApplyUseCase {

    private final OrderRefundViewService orderRefundViewService;
    private final CustomizeRefundGateway customizeRefundGateway;
    private final RefundRepository refundRepository;
    private final IdGen idGen;
    private final Clock clock;

    public RefundApplyUseCase(OrderRefundViewService orderRefundViewService,
                              CustomizeRefundGateway customizeRefundGateway,
                              RefundRepository refundRepository, IdGen idGen, Clock clock) {
        this.orderRefundViewService = orderRefundViewService;
        this.customizeRefundGateway = customizeRefundGateway;
        this.refundRepository = refundRepository;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public RefundApplyResult apply(RefundApplyCmd cmd) {
        // 1) 凭证必填
        if (cmd.proof() == null || cmd.proof().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退款凭证必填");
        }
        // 2) 订单及其退款项（行实付、已退、运费、是否已发货）
        OrderRefundView view = orderRefundViewService.getView(cmd.orderNo())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单不存在"));
        // 3) 定制已排产不可退款
        for (RefundApplyLineCmd line : cmd.lines()) {
            if (customizeRefundGateway.isRefundBlocked(cmd.orderNo(), line.skuId())) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "定制已排产，不可退款");
            }
        }
        // 4) 逐行 + 整单超退校验
        List<RefundCalculator.RefundLine> refundLines = new ArrayList<>();
        long totalRequested = 0L;
        for (RefundApplyLineCmd line : cmd.lines()) {
            RefundLineView v = view.items().stream()
                    .filter(i -> i.skuId() == line.skuId())
                    .findFirst()
                    .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单行不存在"));
            RefundCalculator.RefundLine rl = new RefundCalculator.RefundLine(v.paidAmountCent(), v.alreadyRefundedCent());
            RefundCalculator.validateLineRefund(rl, line.amountCent());
            refundLines.add(rl);
            totalRequested += line.amountCent();
        }
        RefundCalculator.RefundContext ctx = new RefundCalculator.RefundContext(refundLines, view.shippingFeeCent(), view.shipped());
        RefundCalculator.validateWholeRefund(ctx, totalRequested);
        // 5) 生成待审核退款单
        String refundNo = idGen.next("RF");
        RefundOrder ro = new RefundOrder(refundNo, cmd.orderNo(), view.orderId(), totalRequested, "REFUND",
                cmd.reason(), RefundStatus.PENDING, clock.instant().toEpochMilli());
        refundRepository.save(ro);
        return new RefundApplyResult(refundNo, cmd.orderNo(), ro.getStatus().name(), totalRequested);
    }
}
