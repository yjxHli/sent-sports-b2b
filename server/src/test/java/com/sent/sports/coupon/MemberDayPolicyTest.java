package com.sent.sports.coupon;

import com.sent.sports.coupon.service.MemberDayPolicy;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会员日策略（TDD 红）：周三/周四为会员日；下次会员日 00:00；
 * 会员日领券有效期按券型——无门槛小额券 3~7 天、满减券 15 天、其余沿用模板值。
 */
class MemberDayPolicyTest extends BaseUnitTest {

    @Test
    void wednesdayAndThursdayAreMemberDays() {
        assertTrue(MemberDayPolicy.isMemberDay(LocalDate.of(2026, 11, 18))); // 周三
        assertTrue(MemberDayPolicy.isMemberDay(LocalDate.of(2026, 11, 19))); // 周四
        assertFalse(MemberDayPolicy.isMemberDay(LocalDate.of(2026, 11, 20))); // 周五
        assertFalse(MemberDayPolicy.isMemberDay(LocalDate.of(2026, 11, 23))); // 周一
    }

    @Test
    void nextMemberDayFromFridayIsUpcomingWednesday() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 20, 15, 30); // 周五
        LocalDateTime next = MemberDayPolicy.nextMemberDayAt(from);

        assertEquals(LocalDate.of(2026, 11, 25), next.toLocalDate(), "周五之后最近会员日是下周三");
        assertEquals(DayOfWeek.WEDNESDAY, next.getDayOfWeek());
        assertEquals(0, next.getHour(), "下次会员日从 00:00 开始");
        assertEquals(0, next.getMinute());
    }

    @Test
    void nextMemberDayFromTuesdayIsTomorrow() {
        LocalDateTime next = MemberDayPolicy.nextMemberDayAt(LocalDateTime.of(2026, 11, 24, 9, 0));
        assertEquals(LocalDate.of(2026, 11, 25), next.toLocalDate());
    }

    @Test
    void nextMemberDayFromThursdayIsNextWeek() {
        LocalDateTime next = MemberDayPolicy.nextMemberDayAt(LocalDateTime.of(2026, 11, 19, 10, 0));
        assertEquals(LocalDate.of(2026, 11, 25), next.toLocalDate(), "周四的下一次是下周三");
        assertEquals(DayOfWeek.WEDNESDAY, next.getDayOfWeek());
    }

    @Test
    void invalidDaysFallback() {
        assertEquals(3, MemberDayPolicy.claimValidDays("NO_THRESHOLD", 500L, 30), "¥5 无门槛券 3 天");
        assertEquals(7, MemberDayPolicy.claimValidDays("NO_THRESHOLD", 1000L, 30), "其余无门槛券 7 天");
        assertEquals(15, MemberDayPolicy.claimValidDays("FULL_REDUCTION", 1000L, 30), "满减券 15 天");
        assertEquals(15, MemberDayPolicy.claimValidDays("FULL_REDUCTION", 5000L, 30), "满80减50 也是 15 天");
        assertEquals(30, MemberDayPolicy.claimValidDays("CATEGORY", 0L, 30), "品类券沿用模板");
        assertEquals(60, MemberDayPolicy.claimValidDays("ENTERPRISE", 10000L, 60), "企业券沿用模板");
    }
}
