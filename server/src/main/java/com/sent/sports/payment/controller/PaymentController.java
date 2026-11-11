package com.sent.sports.payment.controller;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.payment.dto.PaymentDtos;
import com.sent.sports.payment.service.CreatePaymentUseCase;
import com.sent.sports.payment.service.PayCallbackUseCase;
import com.sent.sports.payment.service.PaymentQueryService;
import com.sent.sports.payment.service.dto.CreatePaymentCmd;
import com.sent.sports.payment.service.dto.CreatePaymentResult;
import com.sent.sports.payment.service.dto.PayCallbackCmd;
import com.sent.sports.payment.service.dto.PayCallbackResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付控制器（P4-19）：发起支付 / 支付结果轮询 / 我的流水 / 模拟回调（plan §8.5）。
 * <p>依赖同模块用例（{@link CreatePaymentUseCase} / {@link PayCallbackUseCase}）与查询门面
 * {@link PaymentQueryService}（桩，P5 接入仓储）。回调验签（plan §7.3 步骤1）由支付网关/拦截器在
 * Phase 5 完成，此处直接委托用例；当前用户绑定（@CurrentUserArg）亦在 Phase 5。</p>
 */
@RestController
@RequestMapping("/api")
public class PaymentController {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final PayCallbackUseCase payCallbackUseCase;
    private final PaymentQueryService paymentQueryService;

    public PaymentController(CreatePaymentUseCase createPaymentUseCase,
                             PayCallbackUseCase payCallbackUseCase,
                             PaymentQueryService paymentQueryService) {
        this.createPaymentUseCase = createPaymentUseCase;
        this.payCallbackUseCase = payCallbackUseCase;
        this.paymentQueryService = paymentQueryService;
    }

    @PostMapping("/payments")
    @RequirePerm("payment:create")
    public R<PaymentDtos.PayResultVO> create(@Valid @RequestBody PaymentDtos.PayCmd cmd) {
        CreatePaymentResult r = createPaymentUseCase.create(
                new CreatePaymentCmd(cmd.orderNo(), cmd.amountCent(), cmd.channel(), cmd.idemKey()));
        return R.ok(toVo(r));
    }

    @GetMapping("/orders/{orderNo}/pay-result")
    public R<PaymentDtos.PayResultVO> payResult(@PathVariable String orderNo) {
        return R.ok(paymentQueryService.getPayResult(orderNo));
    }

    @GetMapping("/payments")
    public R<PageResult<PaymentDtos.PayResultVO>> list(@ModelAttribute PageQuery page) {
        return R.ok(paymentQueryService.listMyPayments(page));
    }

    @PostMapping("/payment/mock/callback")
    public R<PaymentDtos.PayResultVO> callback(@Valid @RequestBody PaymentDtos.CallbackCmd cmd) {
        PayCallbackResult r = payCallbackUseCase.handle(new PayCallbackCmd(
                cmd.txnNo(), cmd.orderNo(), cmd.amountCent(), cmd.channel(),
                cmd.payChannelTradeNo(), cmd.paidAtMillis()));
        return R.ok(toVo(r));
    }

    private PaymentDtos.PayResultVO toVo(CreatePaymentResult r) {
        return new PaymentDtos.PayResultVO(r.orderNo(), r.status(), r.txnNo(),
                r.amountCent(), MoneyUtil.toYuan(r.amountCent()));
    }

    private PaymentDtos.PayResultVO toVo(PayCallbackResult r) {
        return new PaymentDtos.PayResultVO(r.orderNo(), r.status(), r.txnNo(), null, null);
    }
}
