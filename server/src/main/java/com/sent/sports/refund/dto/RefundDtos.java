package com.sent.sports.refund.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 退款接口 Web 层 DTO（P4-21）。
 * 请求体承载外部入参并带校验注解；响应体 {@link RefundVO} 暴露退款单状态与金额（分 + 元）。
 */
public final class RefundDtos {

    private RefundDtos() {
    }

    /** 申请退款请求：订单号、逐行退款金额、原因、凭证（必填）。 */
    public record ApplyRefundCmd(
            @NotBlank(message = "订单号不能为空") String orderNo,
            @NotNull(message = "退款行不能为空") List<RefundLineCmd> lines,
            String reason,
            @NotBlank(message = "退款凭证不能为空") String proof) {
    }

    /** 申请退款单行：SKU + 申请退款金额（分）。 */
    public record RefundLineCmd(
            @NotNull(message = "SKU不能为空") Long skuId,
            @NotNull(message = "金额不能为空") @Positive(message = "金额必须大于0") Long amountCent) {
    }

    /** 退款响应：退款单号 + 订单号 + 状态 + 金额（分 + 元）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RefundVO(String refundNo, String orderNo, String status, Long amountCent, String amountYuan) {
    }

    /** 用户端售后详情：在列表字段基础上补充类型 / 原因 / 申请人 / 申诉次数 / 时间线。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RefundDetailVO(
            String refundNo, String orderNo, String status,
            Long amountCent, String amountYuan,
            String type, String reason, String applicantType,
            int appealCount, Long createdAtMillis, Long refundedAtMillis) {
    }

    /** 管理端审核动作请求：approved=true 走 APPROVE（需确认令牌），false 走 REJECT（原因必填）。 */
    public record AuditCmd(@NotNull(message = "审核结论不能为空") Boolean approved, String remark) {
    }

    /** 管理端退款审核列表行（对齐 packages/shared/src/types.ts 的 RefundVO）。 */
    public record RefundAuditVO(String refundNo, String orderNo, String status, long amountCent,
                                String reason, long createdAt) {
    }
}
