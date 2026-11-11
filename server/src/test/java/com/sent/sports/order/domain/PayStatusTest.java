package com.sent.sports.order.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-21 支付状态与订单联动测试（TDD 红）：未支付/已支付/部分退款/全额退款迁移。 */
class PayStatusTest {

    @Test
    void fiveStatuses() {
        assertEquals(5, PayStatus.values().length);
        assertEquals("已支付", PayStatus.PAID.getChineseName());
        assertEquals("全额退款", PayStatus.FULL_REFUNDED.getChineseName());
    }

    @Test
    void payFlow() {
        assertEquals(PayStatus.PAYING, PayStateMachine.transit(PayStatus.UNPAID, PayAction.PAY_START));
        assertEquals(PayStatus.PAID, PayStateMachine.transit(PayStatus.PAYING, PayAction.PAY_SUCCESS));
        // 回调直达
        assertEquals(PayStatus.PAID, PayStateMachine.transit(PayStatus.UNPAID, PayAction.PAY_SUCCESS));
        // 失败回退
        assertEquals(PayStatus.UNPAID, PayStateMachine.transit(PayStatus.PAYING, PayAction.PAY_FAIL));
    }

    @Test
    void refundFlow() {
        assertEquals(PayStatus.PART_REFUNDED, PayStateMachine.transit(PayStatus.PAID, PayAction.PART_REFUND));
        assertEquals(PayStatus.FULL_REFUNDED, PayStateMachine.transit(PayStatus.PAID, PayAction.FULL_REFUND));
        assertEquals(PayStatus.FULL_REFUNDED, PayStateMachine.transit(PayStatus.PART_REFUNDED, PayAction.FULL_REFUND));
        assertTrue(PayStatus.FULL_REFUNDED.isTerminal());
    }

    @Test
    void illegalPayTransition() {
        BizException ex = assertThrows(BizException.class,
                () -> PayStateMachine.transit(PayStatus.PAID, PayAction.PAY_START));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        assertFalse(PayStatus.UNPAID.isTerminal());
    }
}
