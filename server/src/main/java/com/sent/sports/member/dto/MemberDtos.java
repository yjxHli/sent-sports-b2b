package com.sent.sports.member.dto;

import com.sent.sports.member.domain.MemberLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 会员接口 Web 层 DTO（P4-27）。
 * 请求体承载外部入参并带校验注解；响应体暴露等级进度 / 积分 / 额度应收 / 专属商品（分 + 元）。
 */
public final class MemberDtos {

    private MemberDtos() {
    }

    /** 人工授级请求：客户、目标等级、有效期截止毫秒、原因。 */
    public record GrantLevelCmd(
            @NotNull(message = "客户ID不能为空") Long customerId,
            @NotNull(message = "目标等级不能为空") MemberLevel targetLevel,
            @NotNull(message = "有效期不能为空") Long validToMillis,
            @NotBlank(message = "原因不能为空") String reason) {
    }

    /** 积分调整请求：客户、增减额（可负）、原因、业务类型、业务单号。 */
    public record AdjustPointCmd(
            @NotNull(message = "客户ID不能为空") Long customerId,
            @NotNull(message = "增减额不能为空") Long delta,
            @NotBlank(message = "原因不能为空") String reason,
            String bizType,
            String bizNo) {
    }

    /** 等级进度响应：当前等级、成长值、当前/下一档门槛、进度比例、授级有效期。 */
    public record LevelVO(Long customerId, String level, long growthValue,
                          long currentThreshold, long nextThreshold, double progress, long validToMillis) {
    }

    /** 积分明细响应。 */
    public record PointVO(Long customerId, long availablePoints) {
    }

    /**
     * 额度与应收响应（分 + 元）。
     * <p>除总额度与应收外，额外回传已用额度与可用额度：管理端首屏直接展示「可用额度」，
     * 不再由前端二次计算，避免与 {@code CreditFacade} 的可用口径漂移。</p>
     */
    public record CreditVO(Long customerId, long creditLimitCent, String creditLimitYuan,
                           long receivableCent, String receivableYuan,
                           long creditUsedCent, long availableCent) {

        /** 兼容旧调用方（只关心总额度与应收）的构造入口。 */
        public CreditVO(Long customerId, long creditLimitCent, String creditLimitYuan,
                        long receivableCent, String receivableYuan) {
            this(customerId, creditLimitCent, creditLimitYuan, receivableCent, receivableYuan,
                    0L, creditLimitCent);
        }
    }

    /** 专属商品响应（分 + 元）。 */
    public record ExclusiveProductVO(String productId, String productName, long priceCent, String priceYuan) {
    }

    /** 授级结果响应。 */
    public record GrantResultVO(Long customerId, String level, long validToMillis) {
    }

    /** 积分调整结果响应。 */
    public record AdjustResultVO(Long customerId, long beforePoints, long afterPoints, long delta) {
    }
}
