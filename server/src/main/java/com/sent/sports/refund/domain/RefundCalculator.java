package com.sent.sports.refund.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.List;

/**
 * 退款金额计算（plan §7.6 / spec §6.7.2）：
 * <pre>
 *   行可退上限 = 行实付 = 行金额 − 行优惠分摊
 *   本次可退   = 行可退上限 − 已退额
 *   整单退     = Σ 行实付 + 运费（仅未发货时）
 *   部分退     = 仅按行，不退运费、不补扣运费
 *   超退抛 REFUND_OVERFLOW(40940)
 * </pre>
 */
public final class RefundCalculator {

    private RefundCalculator() {
    }

    /** 一行：行实付金额（已扣优惠分摊）与已退金额，单位均为分。 */
    public record RefundLine(long paidAmountCent, long alreadyRefundedCent) {
    }

    /** 整单上下文：各商品行、运费、是否已发货。 */
    public record RefundContext(List<RefundLine> lines, long shippingFeeCent, boolean shipped) {
    }

    /** 单行可退上限。 */
    public static long lineRefundable(RefundLine line) {
        return Math.max(0, line.paidAmountCent() - line.alreadyRefundedCent());
    }

    /** 整单最大可退：Σ 行可退上限 +（未发货时含运费）。 */
    public static long maxRefundable(RefundContext ctx) {
        long sum = 0;
        for (RefundLine l : ctx.lines()) {
            sum += lineRefundable(l);
        }
        if (!ctx.shipped()) {
            sum += ctx.shippingFeeCent();
        }
        return sum;
    }

    /** 单行退款校验：超出可退上限抛 REFUND_OVERFLOW(40940)。 */
    public static void validateLineRefund(RefundLine line, long requestedCent) {
        if (requestedCent > lineRefundable(line)) {
            throw new BizException(ErrorCode.REFUND_OVERFLOW);
        }
    }

    /** 整单退款校验：超出可退上限抛 REFUND_OVERFLOW(40940)。 */
    public static void validateWholeRefund(RefundContext ctx, long requestedCent) {
        if (requestedCent > maxRefundable(ctx)) {
            throw new BizException(ErrorCode.REFUND_OVERFLOW);
        }
    }
}
