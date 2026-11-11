package com.sent.sports.chat.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 客服分配策略（plan §7.8，验收 A20）：
 * 取在线且未满负荷的客服中，当前会话数最少者；并列按工号升序；
 * 无人可接待返回空（由上层转排队 WAITING）。
 */
public final class StaffAssignmentPolicy {

    private StaffAssignmentPolicy() {
    }

    /** 客服接待能力快照。 */
    public record StaffPresence(long staffId, boolean online, int activeSessions, int maxSessions) {
    }

    public static Optional<StaffPresence> assign(List<StaffPresence> staffs) {
        return staffs.stream()
                .filter(s -> s.online() && s.activeSessions() < s.maxSessions())
                .min(Comparator.comparingInt(StaffPresence::activeSessions)
                        .thenComparingLong(StaffPresence::staffId));
    }
}
