package com.sent.sports.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 支付接口 Web 层 DTO（P4-18）。
 * 请求体承载外部入参并带校验注解；响应体 {@link PayResultVO} 暴露流水/状态/金额（分 + 元）。
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

    /** 支付回调请求：渠道流水号、订单号、金额（分）、渠道、渠道交易号、支付完成时间（毫秒）。 */
    public record CallbackCmd(
            @NotBlank(message = "渠道流水号不能为空") String txnNo,
            @NotBlank(message = "订单号不能为空") String orderNo,
            @NotNull(message = "金额不能为空") Long amountCent,
            @NotBlank(message = "渠道不能为空") String channel,
            String payChannelTradeNo,
            long paidAtMillis) {
    }

    /** 支付结果响应：订单号 + 状态（CREATED/PAID/FAILED/PENDING/ALREADY_PAID 等）+ 流水号 + 金额（分 + 元）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PayResultVO(String orderNo, String status, String txnNo, Long amountCent, String amountYuan) {
    }
}
