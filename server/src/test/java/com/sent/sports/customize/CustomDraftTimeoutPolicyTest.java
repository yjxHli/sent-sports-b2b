package com.sent.sports.customize;

import com.sent.sports.customize.domain.CustomDraftTimeoutPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 图稿上传时限策略单测：72h 截止、12h 提醒节奏、剩余时间文案。 */
class CustomDraftTimeoutPolicyTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 20, 10, 0);

    @Test
    @DisplayName("截止时刻 = 支付时刻 + 72 小时，到点即算超时")
    void deadlineIsSeventyTwoHoursAfterPayment() {
        assertEquals(PAID_AT.plusHours(72), CustomDraftTimeoutPolicy.deadlineOf(PAID_AT));
        assertFalse(CustomDraftTimeoutPolicy.isOverdue(PAID_AT, PAID_AT.plusHours(71)));
        assertTrue(CustomDraftTimeoutPolicy.isOverdue(PAID_AT, PAID_AT.plusHours(72)));
        assertTrue(CustomDraftTimeoutPolicy.isOverdue(PAID_AT, PAID_AT.plusHours(73)));
    }

    @Test
    @DisplayName("首次提醒在支付后 12 小时，之后每 12 小时一次，超时后不再提醒")
    void remindsEveryTwelveHoursUntilDeadline() {
        assertFalse(CustomDraftTimeoutPolicy.shouldRemind(PAID_AT, null, PAID_AT.plusHours(11)),
                "未满 12 小时不提醒");
        assertTrue(CustomDraftTimeoutPolicy.shouldRemind(PAID_AT, null, PAID_AT.plusHours(12)),
                "满 12 小时发首次提醒");
        assertFalse(CustomDraftTimeoutPolicy.shouldRemind(PAID_AT, PAID_AT.plusHours(12), PAID_AT.plusHours(23)),
                "距上次提醒未满 12 小时");
        assertTrue(CustomDraftTimeoutPolicy.shouldRemind(PAID_AT, PAID_AT.plusHours(12), PAID_AT.plusHours(24)),
                "距上次提醒满 12 小时再提醒");
        assertFalse(CustomDraftTimeoutPolicy.shouldRemind(PAID_AT, PAID_AT.plusHours(60), PAID_AT.plusHours(72)),
                "已超时：接下来是取消退款，不该继续催");
    }

    @Test
    @DisplayName("剩余时间文案：天/小时/分钟三档，且不出现 0 天 0 小时这种写法")
    void formatsRemainingTextInThreeTiers() {
        LocalDateTime deadline = CustomDraftTimeoutPolicy.deadlineOf(PAID_AT);
        assertEquals("2 天 12 小时",
                CustomDraftTimeoutPolicy.remainingText(deadline, PAID_AT.plusHours(12)));
        assertEquals("3 天",
                CustomDraftTimeoutPolicy.remainingText(deadline, PAID_AT));
        assertEquals("5 小时 30 分钟",
                CustomDraftTimeoutPolicy.remainingText(deadline, PAID_AT.plusHours(66).plusMinutes(30)));
        assertEquals("5 小时",
                CustomDraftTimeoutPolicy.remainingText(deadline, PAID_AT.plusHours(67)));
        assertEquals("23 分钟",
                CustomDraftTimeoutPolicy.remainingText(deadline, PAID_AT.plusHours(71).plusMinutes(37)));
        assertEquals("0 分钟",
                CustomDraftTimeoutPolicy.remainingText(deadline, PAID_AT.plusHours(72)));
    }
}
