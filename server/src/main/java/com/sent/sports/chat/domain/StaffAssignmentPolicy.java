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

    /**
     * 单坐席（2026-11-16）：系统只用 9001 接待，客户会话统一分配给他。
     * 其他客服（9003 等）即使在线也不参与自动分配——保留 presence 是为了
     * 历史报表/手动分配，但进线分配只认 9001。
     *
     * <p>规则放在 domain 里而不是某个用例里：进线建会话与机器人转人工**两条链路**都要用，
     * 各自抄一份字面量迟早会漂移（一个改 9001、一个还留着旧工号）。</p>
     */
    public static final long SINGLE_AGENT_STAFF_ID = 9001L;

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
