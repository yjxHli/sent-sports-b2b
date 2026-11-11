package com.sent.sports.order.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** P2-15 订单状态机测试（TDD 红）：覆盖 §15.1 全部迁移；非法迁移抛 40901；含 PART_SHIPPED 往返。 */
class OrderStateMachineTest {

    @Test
    void payFromWaitPay() {
        assertEquals(OrderStatus.WAIT_SHIP, OrderStateMachine.transit(OrderStatus.WAIT_PAY, OrderAction.PAY));
    }

    @Test
    void cancelFromWaitPay() {
        assertEquals(OrderStatus.CANCELLED, OrderStateMachine.transit(OrderStatus.WAIT_PAY, OrderAction.USER_CANCEL));
        assertEquals(OrderStatus.CANCELLED, OrderStateMachine.transit(OrderStatus.WAIT_PAY, OrderAction.TIMEOUT_CANCEL));
    }

    @Test
    void latePayCallbackStaysCancelled() {
        assertEquals(OrderStatus.CANCELLED, OrderStateMachine.transit(OrderStatus.CANCELLED, OrderAction.LATE_PAY_CALLBACK));
    }

    @Test
    void shipPartialAndFull() {
        assertEquals(OrderStatus.PART_SHIPPED, OrderStateMachine.transit(OrderStatus.WAIT_SHIP, OrderAction.SHIP_PARTIAL));
        assertEquals(OrderStatus.WAIT_RECEIVE, OrderStateMachine.transit(OrderStatus.WAIT_SHIP, OrderAction.SHIP_FULL));
        assertEquals(OrderStatus.PART_SHIPPED, OrderStateMachine.transit(OrderStatus.PART_SHIPPED, OrderAction.SHIP_CONTINUE));
        assertEquals(OrderStatus.WAIT_RECEIVE, OrderStateMachine.transit(OrderStatus.PART_SHIPPED, OrderAction.SHIP_FULL));
    }

    @Test
    void confirmReceive() {
        assertEquals(OrderStatus.COMPLETED, OrderStateMachine.transit(OrderStatus.WAIT_RECEIVE, OrderAction.CONFIRM_RECEIVE));
        // 部分发货也可在全部包裹确认后完成
        assertEquals(OrderStatus.COMPLETED, OrderStateMachine.transit(OrderStatus.PART_SHIPPED, OrderAction.CONFIRM_RECEIVE));
    }

    @Test
    void adminCloseAndRefundDone() {
        assertEquals(OrderStatus.CLOSED, OrderStateMachine.transit(OrderStatus.WAIT_SHIP, OrderAction.ADMIN_CLOSE));
        assertEquals(OrderStatus.CLOSED, OrderStateMachine.transit(OrderStatus.PART_SHIPPED, OrderAction.ADMIN_CLOSE));
        assertEquals(OrderStatus.CLOSED, OrderStateMachine.transit(OrderStatus.WAIT_SHIP, OrderAction.REFUND_DONE));
        assertEquals(OrderStatus.CLOSED, OrderStateMachine.transit(OrderStatus.PART_SHIPPED, OrderAction.REFUND_DONE));
        assertEquals(OrderStatus.CLOSED, OrderStateMachine.transit(OrderStatus.WAIT_RECEIVE, OrderAction.REFUND_DONE));
        assertEquals(OrderStatus.CLOSED, OrderStateMachine.transit(OrderStatus.COMPLETED, OrderAction.REFUND_DONE));
    }

    @Test
    void illegalTransitionThrows() {
        BizException ex1 = assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.WAIT_SHIP, OrderAction.PAY));
        assertEquals(ErrorCode.STATE_CONFLICT, ex1.getErrorCode());

        BizException ex2 = assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.WAIT_PAY, OrderAction.CONFIRM_RECEIVE));
        assertEquals(ErrorCode.STATE_CONFLICT, ex2.getErrorCode());

        assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.COMPLETED, OrderAction.SHIP_PARTIAL));
    }

    @Test
    void partShippedRoundTrip() {
        OrderStatus s = OrderStateMachine.transit(OrderStatus.WAIT_SHIP, OrderAction.SHIP_PARTIAL); // PART_SHIPPED
        s = OrderStateMachine.transit(s, OrderAction.SHIP_CONTINUE);                                // PART_SHIPPED
        s = OrderStateMachine.transit(s, OrderAction.SHIP_FULL);                                    // WAIT_RECEIVE
        assertEquals(OrderStatus.WAIT_RECEIVE, s);
    }
}
