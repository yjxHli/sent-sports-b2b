package com.sent.sports.payment.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.member.service.PointFacade;
import com.sent.sports.order.service.OrderPaymentService;
import com.sent.sports.order.service.dto.OrderPaymentView;
import com.sent.sports.payment.domain.PaymentTxn;
import com.sent.sports.payment.domain.PaymentTxnStatus;
import com.sent.sports.payment.gateway.PayGateway;
import com.sent.sports.payment.repository.PaymentRepository;
import com.sent.sports.payment.service.dto.PayCallbackCmd;
import com.sent.sports.payment.service.dto.PayCallbackResult;
import com.sent.sports.refund.service.RefundService;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 支付回调用例（P3-13）：实现 plan §7.3 七步与验收 A2/A14。
 * 依赖订单 / 退款模块的门面（中立 DTO），不直接依赖其领域类型，符合 ArchUnit 规则 4。
 *
 * 流程：
 * 1) <b>验签</b>：最先执行，未签名 / 签名不符直接抛异常拦截（P0 修复，审计 R2 之前的安全缺口）；
 * 2) 幂等：按渠道流水号查重，已处理过直接返回 ALREADY_PAID，不重复入账 / 不重复迁移；
 * 3) 加载订单支付态，金额不一致返回 MISMATCH；
 * 4) 已取消订单的迟到支付：生成仅退款单（PAID_AFTER_CANCEL），订单保持取消，流水标记已支付 → REFUNDED；
 * 5) 待付款：经门面锁定订单迁移 待付款→待发货 + 已支付，并发布 OrderPaidEvent；
 *    随后扣减本单抵扣的积分（余额不足则整笔回滚、回调按失败返回）→ SUCCESS；
 * 6) 已支付 / 已发货等终态：仅记录流水，幂等返回 ALREADY_PAID（A14）。
 */
@Service
public class PayCallbackUseCase {

    private static final String STATUS_WAIT_PAY = "WAIT_PAY";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String REASON_PAID_AFTER_CANCEL = "PAID_AFTER_CANCEL";

    private final PaymentRepository paymentRepository;
    private final OrderPaymentService orderPaymentService;
    private final RefundService refundService;
    private final PayGateway payGateway;
    private final PointFacade pointFacade;
    private final Clock clock;

    public PayCallbackUseCase(PaymentRepository paymentRepository, OrderPaymentService orderPaymentService,
                              RefundService refundService, PayGateway payGateway,
                              PointFacade pointFacade, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.orderPaymentService = orderPaymentService;
        this.refundService = refundService;
        this.payGateway = payGateway;
        this.pointFacade = pointFacade;
        this.clock = clock;
    }

    @UseCase
    public PayCallbackResult handle(PayCallbackCmd cmd) {
        // 1) 验签：任何业务读取之前先判「这条通知是不是渠道发的」。
        //    没有这一步，任何人只要 POST 一个订单号 + 金额就能把订单改成已支付（审计 Top5 第 2 项）。
        if (!payGateway.verify(cmd.notifyParams())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付回调验签失败");
        }

        // 2) 幂等：只有「已处理的流水」才算重复回调。
        // 发起支付时本仓储已写入一条 CREATED 流水，它代表支付意图而非支付结果，
        // 必须继续走下面的落账流程（并复用该流水，保留原 idemKey），否则整条支付链路永远停在待支付。
        Optional<PaymentTxn> existing = paymentRepository.findByTxnNo(cmd.txnNo());
        if (existing.isPresent() && existing.get().getStatus() != PaymentTxnStatus.CREATED) {
            return PayCallbackResult.alreadyPaid(cmd);
        }

        // 3) 加载订单支付态并校验金额
        OrderPaymentView view = orderPaymentService.getView(cmd.orderNo())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单不存在"));
        if (view.payableCent() != cmd.amountCent()) {
            return PayCallbackResult.mismatch(cmd);
        }

        // 已支付态流水（渠道侧支付成功，记录入账）：复用发起支付时写入的 CREATED 流水，保留幂等键
        PaymentTxn txn = existing.orElseGet(() -> new PaymentTxn(cmd.txnNo(), cmd.orderNo(), cmd.amountCent(),
                cmd.channel(), cmd.txnNo(), clock.instant().toEpochMilli()));
        txn.markPaid(cmd.payChannelTradeNo());

        // 4) 已取消订单的迟到支付：生成仅退款单，订单保持取消（A2）
        if (STATUS_CANCELLED.equals(view.status())) {
            refundService.createOnlyRefund(cmd.orderNo(), cmd.amountCent(), REASON_PAID_AFTER_CANCEL);
            paymentRepository.save(txn);
            return PayCallbackResult.refunded(cmd);
        }

        // 5) 待付款：锁定订单迁移 待付款 → 待发货 + 已支付，并回写 orders.paid_cent；
        //    paymentRepository 已切真库，下面的 save 会把流水落成/推进成 PAID 行
        if (STATUS_WAIT_PAY.equals(view.status())) {
            orderPaymentService.markPaid(cmd.orderNo(), cmd.amountCent(), cmd.paidAtMillis());
            // 5.1) 支付成功才真正扣减抵扣积分（P5 闭环）：此前只把 point_deduct_cent 记在订单头，
            //      客户余额一分没少，同一批积分可以被无限次抵扣。
            //      扣不动（并发把积分花在别的订单上）时这里抛 POINT_NOT_ENOUGH，
            //      本用例由 @UseCase 包事务 → 订单迁移与流水一起回滚，并让渠道重试，
            //      而不是「钱收了、积分白送」。
            pointFacade.deductPoints(view.customerId(), cmd.orderNo(), view.pointDeductCent());
            paymentRepository.save(txn);
            return PayCallbackResult.success(cmd);
        }

        // 6) 已支付 / 已发货等终态：仅记录流水，幂等返回已支付（A14）
        paymentRepository.save(txn);
        return PayCallbackResult.alreadyPaid(cmd);
    }
}
