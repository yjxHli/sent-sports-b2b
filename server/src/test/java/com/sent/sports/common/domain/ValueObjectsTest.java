package com.sent.sports.common.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-45 值对象测试（TDD 红）：幂等键格式校验 + 危险操作枚举的确认短语完整性（plan §10.1）。
 *
 * <p>2026-11-28：{@code ConfirmPhrase} 辅助类已删除（生产零调用，实际校验在
 * {@code ConfirmTokenServiceImpl} 里用 {@code ConfirmAction.phrase()} 完成），
 * 这里只保留对 {@code ConfirmAction} 本身的保护性断言。</p>
 */
class ValueObjectsTest {

    @Test
    void idemKeyValidUuid() {
        String v = "0f8a1c2e-3b4d-4e5f-9a6b-7c8d9e0f1a2b";
        IdemKey k = IdemKey.of(v);
        assertEquals(v, k.value());
        assertTrue(IdemKey.isValid(v));
    }

    @Test
    void idemKeyNullThrows() {
        BizException ex = assertThrows(BizException.class, () -> IdemKey.of(null));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void idemKeyEmptyThrows() {
        BizException ex = assertThrows(BizException.class, () -> IdemKey.of("   "));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void idemKeyTooLongThrows() {
        String longKey = "a".repeat(65);
        BizException ex = assertThrows(BizException.class, () -> IdemKey.of(longKey));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void idemKeyInvalidCharsThrows() {
        BizException ex = assertThrows(BizException.class, () -> IdemKey.of("bad key!"));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void allActionsKeepNonEmptyPhrases() {
        // 7 个历史危险操作 + 1 个六列表批量删除（短语「删除」）。
        // ConfirmAction 仍被 @Confirm 注解与 ConfirmTokenServiceImpl 使用，断言不能跟着辅助类一起删。
        assertEquals(8, ConfirmAction.values().length);
        for (ConfirmAction a : ConfirmAction.values()) {
            assertNotNull(a.phrase());
            assertFalse(a.phrase().isBlank());
        }
    }
}
