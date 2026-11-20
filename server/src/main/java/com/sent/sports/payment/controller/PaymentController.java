package com.sent.sports.payment.controller;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.payment.dto.PaymentDtos;
import com.sent.sports.payment.gateway.PayGateway;
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

import java.util.Map;

/**
 * 支付控制器（P4-19）：发起支付 / 支付结果轮询 / 我的流水 / 渠道异步通知（plan §8.5）。
 *
 * <p><b>回调入口只做透传</b>：整张渠道参数表（含 {@code sign}）原样交给
 * {@link PayCallbackUseCase#handle}，验签在用例最前面完成。接口层不构造、也不补签任何参数——
 * 「自己签名再自己验签」等于没有验签。</p>
 *
 * <p>发起支付时顺带取一次通道回执（{@link PayGateway#create}）并把签名参数下发给调用方：
 * 演示环境的前端就是渠道替身，它只能回放这份签过名的回执，不能凭空造一张。
 * 该调用放在接口层而不是 {@code @UseCase} 事务里，避免外部通道调用被 DB 事务包住（审计 R5）。</p>
 */
@RestController
@RequestMapping("/api")
public class PaymentController {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final PayCallbackUseCase payCallbackUseCase;
    private final PaymentQueryService paymentQueryService;
    private final PayGateway payGateway;

    public PaymentController(CreatePaymentUseCase createPaymentUseCase,
                             PayCallbackUseCase payCallbackUseCase,
                             PaymentQueryService paymentQueryService,
                             PayGateway payGateway) {
        this.createPaymentUseCase = createPaymentUseCase;
        this.payCallbackUseCase = payCallbackUseCase;
        this.paymentQueryService = paymentQueryService;
        this.payGateway = payGateway;
    }

    @PostMapping("/payments")
    @RequirePerm("payment:create")
    public R<PaymentDtos.PayResultVO> create(@Valid @RequestBody PaymentDtos.PayCmd cmd) {
        CreatePaymentResult r = createPaymentUseCase.create(
                new CreatePaymentCmd(cmd.orderNo(), cmd.amountCent(), cmd.channel(), cmd.idemKey()));
        PayGateway.PayTicket ticket = payGateway.create(r.txnNo(), r.orderNo(), r.amountCent(), cmd.channel());
        return R.ok(new PaymentDtos.PayResultVO(r.orderNo(), r.status(), r.txnNo(),
                r.amountCent(), MoneyUtil.toYuan(r.amountCent()),
                ticket.sign(), ticket.notifyParams()));
    }

    @GetMapping("/orders/{orderNo}/pay-result")
    public R<PaymentDtos.PayResultVO> payResult(@PathVariable String orderNo) {
        return R.ok(paymentQueryService.getPayResult(orderNo));
    }

    @GetMapping("/payments")
    public R<PageResult<PaymentDtos.PayResultVO>> list(@ModelAttribute PageQuery page) {
        return R.ok(paymentQueryService.listMyPayments(page));
    }

    /**
     * 渠道异步通知（生产入口）：请求体是渠道签名的键值参数，验签失败 40001。
     * <p>真实通道用 form-urlencoded 或 JSON 均可，这里统一按 JSON 键值表收。
     */
    @PostMapping("/payment/callback")
    public R<PaymentDtos.PayResultVO> notify(@RequestBody Map<String, String> params) {
        return handleNotify(params);
    }

    /**
     * 演示 / 联调用的同一个入口（前端在发起支付 1.2s 后扮演渠道触发）。
     * <p>保留原路径是为了兼容既有前端与 E2E 脚本；语义与 {@code /payment/callback} 完全一致：
     * 参数必须是 {@code POST /payments} 下发的那份签名回执，否则一律 40001。</p>
     */
    @PostMapping("/payment/mock/callback")
    public R<PaymentDtos.PayResultVO> mockCallback(@RequestBody Map<String, String> params) {
        return handleNotify(params);
    }

    private R<PaymentDtos.PayResultVO> handleNotify(Map<String, String> params) {
        PayCallbackResult r = payCallbackUseCase.handle(PayCallbackCmd.fromNotify(params));
        return R.ok(new PaymentDtos.PayResultVO(r.orderNo(), r.status(), r.txnNo(), null, null));
    }
}
