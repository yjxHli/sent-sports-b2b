package com.sent.sports.order.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.EnumMap;
import java.util.Map;

/**
 * 支付状态机（纯 Java，无 Spring 依赖）。支付结果只能由回调写入（spec §6.6.3）。
 * 非法迁移抛 {@link ErrorCode#STATE_CONFLICT}(40901)。
 */
public final class PayStateMachine {

    private static final Map<PayStatus, Map<PayAction, PayStatus>> TRANSITIONS = new EnumMap<>(PayStatus.class);

    static {
        put(PayStatus.UNPAID, PayAction.PAY_START, PayStatus.PAYING);
        put(PayStatus.UNPAID, PayAction.PAY_SUCCESS, PayStatus.PAID);
        put(PayStatus.PAYING, PayAction.PAY_SUCCESS, PayStatus.PAID);
        put(PayStatus.PAYING, PayAction.PAY_FAIL, PayStatus.UNPAID);
        put(PayStatus.PAID, PayAction.PART_REFUND, PayStatus.PART_REFUNDED);
        put(PayStatus.PAID, PayAction.FULL_REFUND, PayStatus.FULL_REFUNDED);
        put(PayStatus.PART_REFUNDED, PayAction.FULL_REFUND, PayStatus.FULL_REFUNDED);
    }

    private static void put(PayStatus from, PayAction action, PayStatus to) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(PayAction.class)).put(action, to);
    }

    public static PayStatus transit(PayStatus from, PayAction action) {
        if (from == null || action == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        Map<PayAction, PayStatus> map = TRANSITIONS.get(from);
        if (map == null || !map.containsKey(action)) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        return map.get(action);
    }

    public static boolean canTransit(PayStatus from, PayAction action) {
        if (from == null || action == null) {
            return false;
        }
        Map<PayAction, PayStatus> map = TRANSITIONS.get(from);
        return map != null && map.containsKey(action);
    }
}
