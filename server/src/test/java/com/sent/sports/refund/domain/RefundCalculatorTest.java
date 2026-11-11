package com.sent.sports.refund.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
