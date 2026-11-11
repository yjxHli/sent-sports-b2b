package com.sent.sports.common.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-45 值对象测试（TDD 红）：幂等键格式校验 + 7 个 action 确认短语精确匹配（plan §10.1）。
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
    void confirmPhraseExactMatch() {
        assertTrue(ConfirmPhrase.matches(ConfirmAction.ORDER_CLOSE, "确认关闭"));
        assertFalse(ConfirmPhrase.matches(ConfirmAction.ORDER_CLOSE, "确认关"));
        assertFalse(ConfirmPhrase.matches(ConfirmAction.ORDER_CLOSE, null));
    }

    @Test
    void sevenActionsExactPhrases() {
        assertEquals(7, ConfirmAction.values().length);
        for (ConfirmAction a : ConfirmAction.values()) {
            assertTrue(ConfirmPhrase.matches(a, a.phrase()));
            assertFalse(ConfirmPhrase.matches(a, a.phrase() + "x"));
        }
    }
}
