package com.sent.sports.order.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
