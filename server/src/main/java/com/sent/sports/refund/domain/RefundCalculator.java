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

    /**
     * 退款应退回的抵扣积分（主流电商口径：整单退全额、部分退按商品金额比例，向下取整）。
     *
     * <p>为什么基数是「商品行实付」而不是「订单实付」：积分抵扣的 30% 上限就是按商品金额算的
     * （见 {@code PointDeduction.maxDeductibleCent}），退回也按同一口径才对称；
     * 若按扣掉积分后的实付算，退一半商品会退走 43% 的积分，多退少补都不对账。</p>
     *
     * <p>「整单」有两条判定：退了商品全额，或退满了用户实付（admin 关单、迟到支付的仅退款
     * 只按实付金额退，商品行金额拿不到全额）。任一满足即原额退回，绝不超发。</p>
     *
     * @param deductedPoints 该订单实际扣过的抵扣积分（0 = 没用积分，直接返回 0）
     * @param refundCent     本次退款金额（分）
     * @param goodsPaidCent  订单商品行实付合计（分，不含运费）
     * @param paidCent       订单实付总额（分，含运费，已扣优惠与积分）
     * @return 应退回的积分数（0 ~ deductedPoints）
     */
    public static long pointsToReturn(long deductedPoints, long refundCent, long goodsPaidCent, long paidCent) {
        if (deductedPoints <= 0L || refundCent <= 0L) {
            return 0L;
        }
        // goodsPaidCent <= 0 说明行数据缺失（历史脏单），兜底按整单处理，避免把用户积分吞掉
        if (goodsPaidCent <= 0L || refundCent >= goodsPaidCent || refundCent >= paidCent) {
            return deductedPoints;
        }
        return Math.min(deductedPoints, deductedPoints * refundCent / goodsPaidCent);
    }
}
