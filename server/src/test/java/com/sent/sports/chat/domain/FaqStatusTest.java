package com.sent.sports.chat.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 知识库状态枚举测试（V60）。
 *
 * <p>两种口径必须分开：读库（脏数据）用宽容的 {@link FaqStatus#of(String)} 回落 ENABLED，
 * 管理端写入用严格的 {@link FaqStatus#require(String)} 直接拒绝未知值——
 * 让脏状态进库，用户端「只查启用」的过滤条件就会静默失效。</p>
 */
class FaqStatusTest {

    @Test
    @DisplayName("读库宽容：空值 / 脏值一律按启用处理，不至于让历史数据整条失效")
    void ofFallsBackToEnabled() {
        assertEquals(FaqStatus.ENABLED, FaqStatus.of(null));
        assertEquals(FaqStatus.ENABLED, FaqStatus.of(""));
        assertEquals(FaqStatus.ENABLED, FaqStatus.of("WHATEVER"));
        assertEquals(FaqStatus.DISABLED, FaqStatus.of("DISABLED"));
        assertEquals(FaqStatus.DISABLED, FaqStatus.of(" disabled "));
    }

    @Test
    @DisplayName("写入严格：未知状态直接 PARAM_INVALID，不猜运营想表达什么")
    void requireRejectsUnknown() {
        BizException e = assertThrows(BizException.class, () -> FaqStatus.require("ON"));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        assertThrows(BizException.class, () -> FaqStatus.require(""));
        assertThrows(BizException.class, () -> FaqStatus.require(null));
        assertEquals(FaqStatus.ENABLED, FaqStatus.require("enabled"));
    }
}
