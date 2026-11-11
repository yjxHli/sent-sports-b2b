package com.sent.sports.payment.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.domain.IdemKey;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.dto.OrderPaymentStatusDto;
import com.sent.sports.payment.domain.PaymentTxn;
import com.sent.sports.payment.domain.PaymentTxnStatus;
import com.sent.sports.payment.repository.PaymentRepository;
import com.sent.sports.payment.service.dto.CreatePaymentCmd;
import com.sent.sports.payment.service.dto.CreatePaymentResult;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 发起支付用例（P3-11）：生成支付流水，幂等重放，已支付/终态不可重复发起，金额须与订单应付一致。
 * 通过 order 模块的 OrderQueryService 门面获取订单支付态（中立 DTO），不直接依赖 order 领域类型（ArchUnit 规则 4）。
 */
@Service
public class CreatePaymentUseCase {

    private final OrderQueryService orderQueryService;
    private final PaymentRepository paymentRepository;
    private final IdGen idGen;
    private final Clock clock;

    public CreatePaymentUseCase(OrderQueryService orderQueryService, PaymentRepository paymentRepository,
                               IdGen idGen, Clock clock) {
        this.orderQueryService = orderQueryService;
        this.paymentRepository = paymentRepository;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public CreatePaymentResult create(CreatePaymentCmd cmd) {
        // 1) 幂等键必填
        IdemKey idemKey = IdemKey.of(cmd.idemKey());

        // 2) 幂等：同幂等键直接返回已存在的流水（重放）
        Optional<PaymentTxn> existing = paymentRepository.findByIdemKey(idemKey.value());
        if (existing.isPresent()) {
            return toResult(existing.get());
        }

        // 3) 订单必须存在、可支付、金额一致
        OrderPaymentStatusDto order = orderQueryService.getPaymentStatus(cmd.orderNo())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单不存在"));
        if (!order.payable()) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "订单不可发起支付（已支付或已终态）");
        }
        if (cmd.amountCent() != order.payableCent()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付金额与订单应付不一致");
        }

        // 4) 生成流水并落库
        String txnNo = idGen.next("PR");
        long now = clock.instant().toEpochMilli();
        PaymentTxn txn = new PaymentTxn(txnNo, cmd.orderNo(), cmd.amountCent(), cmd.channel(), idemKey.value(), now);
        paymentRepository.save(txn);

        return toResult(txn);
    }

    private CreatePaymentResult toResult(PaymentTxn txn) {
        return new CreatePaymentResult(txn.getTxnNo(), txn.getStatus().name(), txn.getAmountCent(), txn.getOrderNo());
    }
}
