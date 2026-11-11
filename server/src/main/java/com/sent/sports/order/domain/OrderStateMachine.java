package com.sent.sports.order.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.EnumMap;
import java.util.Map;

/**
 * 订单主状态机（纯 Java，无 Spring 依赖）：维护 {@code from -> (action -> to)} 迁移表。
 * 所有状态变更必须集中走此方法，非法迁移抛 {@link ErrorCode#STATE_CONFLICT}(40901)。
 * 判断逻辑只允许写在此处，页面/接口读取状态只允许调用本机。
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Map<OrderAction, OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        put(OrderStatus.WAIT_PAY, OrderAction.PAY, OrderStatus.WAIT_SHIP);
        put(OrderStatus.WAIT_PAY, OrderAction.USER_CANCEL, OrderStatus.CANCELLED);
        put(OrderStatus.WAIT_PAY, OrderAction.TIMEOUT_CANCEL, OrderStatus.CANCELLED);

        put(OrderStatus.CANCELLED, OrderAction.LATE_PAY_CALLBACK, OrderStatus.CANCELLED);

        put(OrderStatus.WAIT_SHIP, OrderAction.SHIP_PARTIAL, OrderStatus.PART_SHIPPED);
        put(OrderStatus.WAIT_SHIP, OrderAction.SHIP_FULL, OrderStatus.WAIT_RECEIVE);
        put(OrderStatus.WAIT_SHIP, OrderAction.ADMIN_CLOSE, OrderStatus.CLOSED);
        put(OrderStatus.WAIT_SHIP, OrderAction.REFUND_DONE, OrderStatus.CLOSED);

        put(OrderStatus.PART_SHIPPED, OrderAction.SHIP_CONTINUE, OrderStatus.PART_SHIPPED);
        put(OrderStatus.PART_SHIPPED, OrderAction.SHIP_FULL, OrderStatus.WAIT_RECEIVE);
        put(OrderStatus.PART_SHIPPED, OrderAction.CONFIRM_RECEIVE, OrderStatus.COMPLETED);
        put(OrderStatus.PART_SHIPPED, OrderAction.ADMIN_CLOSE, OrderStatus.CLOSED);
        put(OrderStatus.PART_SHIPPED, OrderAction.REFUND_DONE, OrderStatus.CLOSED);

        put(OrderStatus.WAIT_RECEIVE, OrderAction.CONFIRM_RECEIVE, OrderStatus.COMPLETED);
        put(OrderStatus.WAIT_RECEIVE, OrderAction.REFUND_DONE, OrderStatus.CLOSED);

        put(OrderStatus.COMPLETED, OrderAction.REFUND_DONE, OrderStatus.CLOSED);
    }

    private static void put(OrderStatus from, OrderAction action, OrderStatus to) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(OrderAction.class)).put(action, to);
    }

    public static OrderStatus transit(OrderStatus from, OrderAction action) {
        if (from == null || action == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        Map<OrderAction, OrderStatus> map = TRANSITIONS.get(from);
        if (map == null || !map.containsKey(action)) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        return map.get(action);
    }

    public static boolean canTransit(OrderStatus from, OrderAction action) {
        if (from == null || action == null) {
            return false;
        }
        Map<OrderAction, OrderStatus> map = TRANSITIONS.get(from);
        return map != null && map.containsKey(action);
    }
}
