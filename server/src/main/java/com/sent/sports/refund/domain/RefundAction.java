package com.sent.sports.refund.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

/**
 * 退款状态迁移动作（P1-R3）：审核侧 APPROVE / REJECT / APPEAL，
 * 回调侧 REFUND_SUCCESS / REFUND_FAIL。纯 Java 枚举，负责把接口上的动作字符串收敛为强类型。
 */
public enum RefundAction {
    APPROVE,        // 审核通过 → 退款中
    REJECT,         // 审核驳回 → 已关闭
    APPEAL,         // 驳回后申诉一次 → 待审核
    REFUND_SUCCESS, // 回调成功 → 已退款
    REFUND_FAIL;    // 回调失败 → 失败（待人工对账）

    /** 非法动作字符串统一转 PARAM_INVALID，避免魔法字符串散落在用例里。 */
    public static RefundAction of(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知的审核动作");
        }
        for (RefundAction action : values()) {
            if (action.name().equalsIgnoreCase(code.trim())) {
                return action;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的审核动作");
    }
}
