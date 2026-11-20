package com.sent.sports.coupon.service;

import com.sent.sports.coupon.domain.CouponType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会员日策略（演示业务规则，纯静态便于单测）：
 * <ul>
 *   <li>会员日 = 每周三、周四；仅会员日可从领券中心领券。</li>
 *   <li>下次会员日 = 从给定时刻向后（严格大于）第一个周三/周四的 00:00。</li>
 *   <li>会员日领券有效期按券型：规则本身在 {@link CouponType#claimValidDays}，
 *       这里只做「未知券型沿用模板值」的兜底（历史券型可能来自更早的配置）。</li>
 * </ul>
 */
public final class MemberDayPolicy {

    private MemberDayPolicy() {
    }

    public static boolean isMemberDay(LocalDate date) {
        if (date == null) return false;
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.WEDNESDAY || day == DayOfWeek.THURSDAY;
    }

    /** 从 from 向后找第一个会员日的 00:00（若 from 当天是会员日则取下周三，语义为「下一次」）。 */
    public static LocalDateTime nextMemberDayAt(LocalDateTime from) {
        LocalDate cursor = from.toLocalDate().plusDays(1);
        while (!isMemberDay(cursor)) {
            cursor = cursor.plusDays(1);
        }
        return cursor.atStartOfDay();
    }

    /** 会员日领券后的有效期天数；未知券型沿用模板值，其余由券型枚举决定。 */
    public static int claimValidDays(String couponType, long amountCent, int templateValidDays) {
        CouponType type = CouponType.from(couponType);
        return type == null ? templateValidDays : type.claimValidDays(amountCent, templateValidDays);
    }
}
