package com.sent.sports.order.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** P2-19 发货进度重算测试（TDD 红）：0→待发货、部分→部分发货、全发→待收货。 */
class ShipmentProgressTest {

    @Test
    void noneShipped() {
        assertEquals(OrderStatus.WAIT_SHIP, ShipmentProgress.recalc(10, 0));
    }

    @Test
    void partialShipped() {
        assertEquals(OrderStatus.PART_SHIPPED, ShipmentProgress.recalc(10, 5));
        assertEquals(OrderStatus.PART_SHIPPED, ShipmentProgress.recalc(10, 1));
    }

    @Test
    void allShipped() {
        assertEquals(OrderStatus.WAIT_RECEIVE, ShipmentProgress.recalc(10, 10));
        // 超出按全发处理
        assertEquals(OrderStatus.WAIT_RECEIVE, ShipmentProgress.recalc(10, 11));
    }

    @Test
    void invalidTotalRejected() {
        BizException ex = assertThrows(BizException.class, () -> ShipmentProgress.recalc(0, 0));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    /** 发货动作由进度算出，最终准入仍由 OrderStateMachine 裁决。 */
    @Test
    void actionFromWaitShip() {
        assertEquals(OrderAction.SHIP_PARTIAL, ShipmentProgress.actionFor(OrderStatus.WAIT_SHIP, 5, 2));
        assertEquals(OrderAction.SHIP_FULL, ShipmentProgress.actionFor(OrderStatus.WAIT_SHIP, 5, 5));
    }

    @Test
    void actionFromPartShipped() {
        assertEquals(OrderAction.SHIP_CONTINUE, ShipmentProgress.actionFor(OrderStatus.PART_SHIPPED, 5, 3));
        assertEquals(OrderAction.SHIP_FULL, ShipmentProgress.actionFor(OrderStatus.PART_SHIPPED, 5, 5));
    }

    @Test
    void selectedActionStillGoesThroughStateMachine() {
        // WAIT_RECEIVE 上算出的 SHIP_FULL 必须被状态机拒绝，而不是绕过它直接赋值
        OrderAction action = ShipmentProgress.actionFor(OrderStatus.WAIT_RECEIVE, 5, 5);
        assertEquals(OrderAction.SHIP_FULL, action);
        BizException ex = assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.WAIT_RECEIVE, action));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
    }
}
