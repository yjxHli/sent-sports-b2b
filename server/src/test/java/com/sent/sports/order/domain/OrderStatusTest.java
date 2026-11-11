package com.sent.sports.order.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-13 订单状态枚举测试（TDD 红）：7 个状态、中文名、是否终态。 */
class OrderStatusTest {

    @Test
    void hasSevenStatuses() {
        assertEquals(7, OrderStatus.values().length);
    }

    @Test
    void chineseNames() {
        assertEquals("待付款", OrderStatus.WAIT_PAY.getChineseName());
        assertEquals("部分发货", OrderStatus.PART_SHIPPED.getChineseName());
        assertEquals("已关闭", OrderStatus.CLOSED.getChineseName());
    }

    @Test
    void terminalFlags() {
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertTrue(OrderStatus.CLOSED.isTerminal());
        // 已完成可因退款关闭，不是终态
        assertFalse(OrderStatus.COMPLETED.isTerminal());
        assertFalse(OrderStatus.WAIT_PAY.isTerminal());
        assertFalse(OrderStatus.WAIT_SHIP.isTerminal());
        assertFalse(OrderStatus.PART_SHIPPED.isTerminal());
        assertFalse(OrderStatus.WAIT_RECEIVE.isTerminal());
    }
}
