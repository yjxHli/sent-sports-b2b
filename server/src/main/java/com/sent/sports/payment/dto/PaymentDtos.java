package com.sent.sports.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

/**
 * 支付接口 Web 层 DTO（P4-18）。
 * 请求体承载外部入参并带校验注解；响应体 {@link PayResultVO} 暴露流水/状态/金额（分 + 元）。
 *
 * <p>回调不再使用固定字段 DTO：渠道通知是「一组键值 + 签名」，接口层原样收下整张参数表交给用例验签，
 * 业务字段由 {@code PayCallbackCmd.fromNotify(...)} 从中解析。字段式 DTO 会诱使后人只比对字段，
 * 而忘了签名根本没覆盖到这些字段。</p>
 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** 发起支付请求：订单号、金额（分）、渠道、幂等键。 */
    public record PayCmd(
            @NotBlank(message = "订单号不能为空") String orderNo,
            @NotNull(message = "金额不能为空") @Positive(message = "金额必须大于0") Long amountCent,
            @NotBlank(message = "渠道不能为空") String channel,
            @NotBlank(message = "幂等键不能为空") String idemKey) {
    }

    /**
     * 支付结果响应：订单号 + 状态（CREATED/PAID/FAILED/PENDING/ALREADY_PAID 等）+ 流水号 + 金额（分 + 元）。
     *
     * <p>{@code sign} + {@code notifyParams} 只在「发起支付」时下发：那是渠道受理回执，
     * 演示环境的前端原样回放它们来模拟渠道异步通知。签名密钥不下发，
     * 因此前端无法为任意订单 / 任意金额伪造一份通知。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PayResultVO(String orderNo, String status, String txnNo, Long amountCent, String amountYuan,
                              String sign, Map<String, String> notifyParams) {

        /** 兼容只带金额信息的调用方（支付结果轮询 / 回调响应不需要签名）。 */
        public PayResultVO(String orderNo, String status, String txnNo, Long amountCent, String amountYuan) {
            this(orderNo, status, txnNo, amountCent, amountYuan, null, null);
        }
    }
}
