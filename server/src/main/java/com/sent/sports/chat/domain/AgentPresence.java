package com.sent.sports.chat.domain;

/**
 * 客服在线状态快照（plan §7.8 / spec §6.10）：纯 POJO。
 * activeSessions 为当前接待中的会话数，maxSessions 为可同时接待上限。
 */
public class AgentPresence {

    private final long staffId;
    private PresenceStatus presence;
    private int activeSessions;
    private final int maxSessions;
    private long updatedAtMillis;

    public AgentPresence(long staffId, PresenceStatus presence, int activeSessions, int maxSessions,
                         long updatedAtMillis) {
        this.staffId = staffId;
        this.presence = presence;
        this.activeSessions = activeSessions;
        this.maxSessions = maxSessions;
        this.updatedAtMillis = updatedAtMillis;
    }

    public void setPresence(PresenceStatus presence) {
        this.presence = presence;
    }

    public void setUpdatedAtMillis(long updatedAtMillis) {
        this.updatedAtMillis = updatedAtMillis;
    }

    public long getStaffId() {
        return staffId;
    }

    public PresenceStatus getPresence() {
        return presence;
    }

    public int getActiveSessions() {
        return activeSessions;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }
}
