package com.sent.sports.refund.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-29 退款金额测试（TDD 红）：plan §7.6。
 * 行可退上限 = 行实付（行金额 − 优惠分摊）；已退扣减；
 * 超退抛 REFUND_OVERFLOW(40940)；整单未发货含运费、已发货/部分退不含运费（不补扣）。
 */
class RefundCalculatorTest {

    private RefundCalculator.RefundLine line(long paid, long refunded) {
        return new RefundCalculator.RefundLine(paid, refunded);
    }

    @Test
    void lineRefundableSubtractsAlreadyRefunded() {
        assertEquals(10000L, RefundCalculator.lineRefundable(line(10000, 0)));
        assertEquals(7000L, RefundCalculator.lineRefundable(line(10000, 3000)));
    }

    @Test
    void overRefundLineThrows() {
        BizException ex = assertThrows(BizException.class,
                () -> RefundCalculator.validateLineRefund(line(10000, 0), 11000));
        assertEquals(ErrorCode.REFUND_OVERFLOW, ex.getErrorCode());
    }

    @Test
    void wholeOrderUnshippedIncludesShipping() {
        List<RefundCalculator.RefundLine> lines = List.of(line(5000, 0), line(5000, 0));
        RefundCalculator.RefundContext ctx = new RefundCalculator.RefundContext(lines, 1200L, false);
        assertEquals(11200L, RefundCalculator.maxRefundable(ctx));
    }

    @Test
    void wholeOrderShippedExcludesShipping() {
        List<RefundCalculator.RefundLine> lines = List.of(line(5000, 0), line(5000, 0));
        RefundCalculator.RefundContext ctx = new RefundCalculator.RefundContext(lines, 1200L, true);
        assertEquals(10000L, RefundCalculator.maxRefundable(ctx));
    }

    @Test
    void overRefundWholeThrows() {
        List<RefundCalculator.RefundLine> lines = List.of(line(5000, 0));
        RefundCalculator.RefundContext ctx = new RefundCalculator.RefundContext(lines, 1200L, false);
        // 可退上限 = 5000 + 1200 = 6200；请求 7000 超额
        BizException ex = assertThrows(BizException.class,
                () -> RefundCalculator.validateWholeRefund(ctx, 7000));
        assertEquals(ErrorCode.REFUND_OVERFLOW, ex.getErrorCode());
    }

    @Test
    void partialRefundNeverAddsShipping() {
        // 单行部分退：即便整单含运费，按行计算也不计入运费（不补扣）
        RefundCalculator.RefundLine l = line(5000, 0);
        assertEquals(5000L, RefundCalculator.lineRefundable(l));
        RefundCalculator.validateLineRefund(l, 5000); // 不抛异常
    }

    // ==================== 退款退回抵扣积分（主流电商口径） ====================

    /** 订单：商品行实付 10000、实付总额 7000（积分抵了 3000）、实际扣减积分 3000。 */
    private static final long GOODS_PAID = 10_000L;
    private static final long ORDER_PAID = 7_000L;
    private static final long DEDUCTED = 3_000L;

    @Test
    void fullRefundReturnsAllDeductedPoints() {
        // 退满商品额 → 整单，原额退回
        assertEquals(3000L, RefundCalculator.pointsToReturn(DEDUCTED, GOODS_PAID, GOODS_PAID, ORDER_PAID));
        // 退满实付（admin 关单 / 迟到支付的仅退款只退实付）同样视为整单
        assertEquals(3000L, RefundCalculator.pointsToReturn(DEDUCTED, ORDER_PAID, GOODS_PAID, ORDER_PAID));
        // 退款额超过上限也按整单处理，绝不超发
        assertEquals(3000L, RefundCalculator.pointsToReturn(DEDUCTED, 12_000L, GOODS_PAID, ORDER_PAID));
    }

    @Test
    void partialRefundReturnsProportionally() {
        // 退了商品额的一半 → 抵扣 3000 退 1500
        assertEquals(1500L, RefundCalculator.pointsToReturn(DEDUCTED, 5_000L, GOODS_PAID, ORDER_PAID));
        // 向下取整，绝不因为四舍五入把积分退多
        assertEquals(999L, RefundCalculator.pointsToReturn(DEDUCTED, 3_333L, GOODS_PAID, ORDER_PAID));
    }

    @Test
    void noDeductionNoPointsBack() {
        assertEquals(0L, RefundCalculator.pointsToReturn(0L, GOODS_PAID, GOODS_PAID, ORDER_PAID));
        assertEquals(0L, RefundCalculator.pointsToReturn(-100L, GOODS_PAID, GOODS_PAID, ORDER_PAID));
        // 退款额为 0（纯运费争议等）不触发积分退回
        assertEquals(0L, RefundCalculator.pointsToReturn(DEDUCTED, 0L, GOODS_PAID, ORDER_PAID));
    }

    @Test
    void returnedPointsNeverExceedDeducted() {
        // 行数据缺失（商品额为 0）时按整单兜底，且恒 <= 抵扣额
        assertEquals(3000L, RefundCalculator.pointsToReturn(DEDUCTED, 5_000L, 0L, 0L));
        assertEquals(3000L, RefundCalculator.pointsToReturn(DEDUCTED, 1L, 1L, 1L));
        assertTrue(RefundCalculator.pointsToReturn(DEDUCTED, 4_999L, GOODS_PAID, ORDER_PAID) <= DEDUCTED);
    }
}
