package com.sent.sports.refund.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 退款状态机测试（P1-R3）：合法迁移表 + 全矩阵非法迁移统一 40901。 */
class RefundStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,APPROVE,REFUNDING",
            "PENDING,REJECT,CLOSED",
            "CLOSED,APPEAL,PENDING",
            "REFUNDING,REFUND_SUCCESS,REFUNDED",
            "REFUNDING,REFUND_FAIL,FAILED"
    })
    void legalTransitions(RefundStatus from, RefundAction action, RefundStatus to) {
        assertEquals(to, RefundStateMachine.transit(from, action));
    }

    /** 5 状态 × 全部动作矩阵：除合法迁移外一律抛 40901。 */
    private static Stream<Arguments> illegalTransitions() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (RefundStatus from : RefundStatus.values()) {
            for (RefundAction action : RefundAction.values()) {
                if (!RefundStateMachine.canTransit(from, action)) {
                    cases.add(Arguments.of(from, action));
                }
            }
        }
        return cases.build();
    }

    @ParameterizedTest
    @MethodSource("illegalTransitions")
    void illegalTransitionThrowsStateConflict(RefundStatus from, RefundAction action) {
        BizException ex = assertThrows(BizException.class, () -> RefundStateMachine.transit(from, action));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
    }

    @Test
    void nullArgumentsRejected() {
        assertEquals(ErrorCode.STATE_CONFLICT, assertThrows(BizException.class,
                () -> RefundStateMachine.transit(null, RefundAction.APPROVE)).getErrorCode());
        assertEquals(ErrorCode.STATE_CONFLICT, assertThrows(BizException.class,
                () -> RefundStateMachine.transit(RefundStatus.PENDING, null)).getErrorCode());
    }

    @Test
    void aggregateTransitUpdatesStatusAndRejectsIllegal() {
        RefundOrder order = new RefundOrder("RF20240101000001", "SO20240101000099", 1L,
                5000L, "REFUND", "reason", RefundStatus.PENDING, 0L);
        order.transit(RefundAction.APPROVE);
        assertEquals(RefundStatus.REFUNDING, order.getStatus());

        BizException ex = assertThrows(BizException.class, () -> order.transit(RefundAction.APPROVE));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        assertEquals(RefundStatus.REFUNDING, order.getStatus());
    }

    @Test
    void unknownActionCodeRejected() {
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> RefundAction.of("NO_SUCH_ACTION")).getErrorCode());
    }
}
