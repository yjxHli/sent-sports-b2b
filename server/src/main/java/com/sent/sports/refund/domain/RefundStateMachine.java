package com.sent.sports.refund.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.EnumMap;
import java.util.Map;

/**
 * 退款单状态机（纯 Java，无 Spring 依赖）：维护 {@code from -> (action -> to)} 迁移表。
 * 所有退款状态变更必须经 {@link RefundOrder#transit(RefundAction)}，非法迁移抛
 * {@link ErrorCode#STATE_CONFLICT}(40901)。写法与 CustomStateMachine / OrderStateMachine 保持一致。
 */
public final class RefundStateMachine {

    private static final Map<RefundStatus, Map<RefundAction, RefundStatus>> TRANSITIONS =
            new EnumMap<>(RefundStatus.class);

    static {
        put(RefundStatus.PENDING, RefundAction.APPROVE, RefundStatus.REFUNDING);
        put(RefundStatus.PENDING, RefundAction.REJECT, RefundStatus.CLOSED);

        put(RefundStatus.CLOSED, RefundAction.APPEAL, RefundStatus.PENDING);

        put(RefundStatus.REFUNDING, RefundAction.REFUND_SUCCESS, RefundStatus.REFUNDED);
        put(RefundStatus.REFUNDING, RefundAction.REFUND_FAIL, RefundStatus.FAILED);
    }

    private static void put(RefundStatus from, RefundAction action, RefundStatus to) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(RefundAction.class)).put(action, to);
    }

    public static RefundStatus transit(RefundStatus from, RefundAction action) {
        if (from == null || action == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        Map<RefundAction, RefundStatus> map = TRANSITIONS.get(from);
        if (map == null || !map.containsKey(action)) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        return map.get(action);
    }

    public static boolean canTransit(RefundStatus from, RefundAction action) {
        if (from == null || action == null) {
            return false;
        }
        Map<RefundAction, RefundStatus> map = TRANSITIONS.get(from);
        return map != null && map.containsKey(action);
    }
}
