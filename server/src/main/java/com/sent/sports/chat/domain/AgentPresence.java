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

    /** 接待一个会话：负载 +1（不在此处拦截上限，上限由分配策略判定）。 */
    public void increaseActive() {
        this.activeSessions += 1;
    }

    /** 结束一个会话：负载 -1，下限 0，避免释放重复调用把负载减成负数。 */
    public void decreaseActive() {
        if (this.activeSessions > 0) {
            this.activeSessions -= 1;
        }
    }

    /** 用真实值重置负载（上线时以库里的 ACTIVE 会话数为准，避免进程重启后漂移）。 */
    public void resetActive(int activeSessions) {
        this.activeSessions = Math.max(0, activeSessions);
    }

    /** 是否还有接待名额（在线且未满上限）。 */
    public boolean hasCapacity() {
        return presence == PresenceStatus.ONLINE && activeSessions < maxSessions;
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
