package com.sent.sports.customize.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.EnumMap;
import java.util.Map;

/**
 * 定制单状态机（纯 Java，无 Spring 依赖）：维护 {@code from -> (action -> to)} 迁移表。
 * 所有变更走 {@link #transit(CustomStatus, CustomAction, int)}，非法迁移抛 {@link ErrorCode#STATE_CONFLICT}(40901)。
 * <p>特例：第 4 次提交（rejectCount >= 3）由 {@code REJECTED -> WAIT_REVIEW} 改转 {@code ESCALATED}。</p>
 */
public final class CustomStateMachine {

    private static final Map<CustomStatus, Map<CustomAction, CustomStatus>> TRANSITIONS = new EnumMap<>(CustomStatus.class);

    static {
        put(CustomStatus.WAIT_DRAFT, CustomAction.SUBMIT_DRAFT, CustomStatus.WAIT_REVIEW);
        put(CustomStatus.WAIT_DRAFT, CustomAction.CANCEL, CustomStatus.CANCELLED);

        put(CustomStatus.WAIT_REVIEW, CustomAction.PASS, CustomStatus.APPROVED);
        put(CustomStatus.WAIT_REVIEW, CustomAction.REJECT, CustomStatus.REJECTED);
        put(CustomStatus.WAIT_REVIEW, CustomAction.CANCEL, CustomStatus.CANCELLED);

        put(CustomStatus.REJECTED, CustomAction.RESUBMIT, CustomStatus.WAIT_REVIEW);
        put(CustomStatus.REJECTED, CustomAction.CANCEL, CustomStatus.CANCELLED);

        put(CustomStatus.APPROVED, CustomAction.START_PRODUCTION, CustomStatus.IN_PRODUCTION);
        put(CustomStatus.IN_PRODUCTION, CustomAction.FINISH, CustomStatus.FINISHED);
        put(CustomStatus.FINISHED, CustomAction.SHIP, CustomStatus.SHIPPED);
    }

    private static void put(CustomStatus from, CustomAction action, CustomStatus to) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(CustomAction.class)).put(action, to);
    }

    /** 便捷重载：rejectCount 默认 0（用于非重新提交场景）。 */
    public static CustomStatus transit(CustomStatus from, CustomAction action) {
        return transit(from, action, 0);
    }

    public static CustomStatus transit(CustomStatus from, CustomAction action, int rejectCount) {
        if (from == null || action == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        Map<CustomAction, CustomStatus> map = TRANSITIONS.get(from);
        if (map == null || !map.containsKey(action)) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        CustomStatus to = map.get(action);
        // 第 4 次提交（驳回累计 >= 3）升级待主管处理，而非回到待审核
        if (action == CustomAction.RESUBMIT && rejectCount >= 3) {
            return CustomStatus.ESCALATED;
        }
        return to;
    }

    public static boolean canTransit(CustomStatus from, CustomAction action) {
        if (from == null || action == null) {
            return false;
        }
        Map<CustomAction, CustomStatus> map = TRANSITIONS.get(from);
        return map != null && map.containsKey(action);
    }
}
