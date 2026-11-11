package com.sent.sports.order.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-17 订单行发货量测试（TDD 红）：超发被拒、多次累加、剩余量计算。 */
class OrderItemTest {

    @Test
    void remainingAndAccumulate() {
        OrderItem item = new OrderItem(1001L, 10);
        assertEquals(10, item.getRemainingQty());
        assertFalse(item.isFullyShipped());

        item.ship(4);
        assertEquals(4, item.getShippedQty());
        assertEquals(6, item.getRemainingQty());
        assertFalse(item.isFullyShipped());

        item.ship(6);
        assertEquals(10, item.getShippedQty());
        assertEquals(0, item.getRemainingQty());
        assertTrue(item.isFullyShipped());
    }

    @Test
    void overShipRejected() {
        OrderItem item = new OrderItem(1001L, 10);
        item.ship(3);
        BizException ex = assertThrows(BizException.class, () -> item.ship(8));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        assertEquals(3, item.getShippedQty());
    }

    @Test
    void invalidQtyRejected() {
        OrderItem item = new OrderItem(1001L, 10);
        assertThrows(BizException.class, () -> item.ship(0));
        assertThrows(BizException.class, () -> item.ship(-1));
    }
}
