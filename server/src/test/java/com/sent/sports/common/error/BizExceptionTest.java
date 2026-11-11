package com.sent.sports.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 业务异常文案（发货工作台 409 排查时发现的共性缺陷）：
 * 项目里 62 处 {@code new BizException(ErrorCode, "具体原因")} 的自定义原因
 * 被 {@link BizException#resolvedMessage()} 的 {@code String.format} 吞掉
 * （默认文案没有 %s 占位符），前端只能看到泛泛的「状态冲突」而无法定位真因。
 */
class BizExceptionTest {

    @Test
    @DisplayName("带具体原因时把原因透传给前端，不被默认文案覆盖")
    void resolvedMessageKeepsExplicitReason() {
        BizException ex = new BizException(ErrorCode.STATE_CONFLICT, "订单当前状态不可发货");

        assertEquals("订单当前状态不可发货", ex.resolvedMessage());
    }

    @Test
    @DisplayName("无参数时仍返回错误码默认文案")
    void resolvedMessageFallsBackToDefault() {
        BizException ex = new BizException(ErrorCode.STATE_CONFLICT);

        assertEquals("状态冲突（当前状态不允许该操作）", ex.resolvedMessage());
    }

    @Test
    @DisplayName("getMessage 保持错误码默认文案，不影响日志与既有断言口径")
    void getMessageKeepsDefault() {
        BizException ex = new BizException(ErrorCode.STATE_CONFLICT, "订单当前状态不可发货");

        assertEquals(ErrorCode.STATE_CONFLICT.getDefaultMessage(), ex.getMessage());
    }
}
