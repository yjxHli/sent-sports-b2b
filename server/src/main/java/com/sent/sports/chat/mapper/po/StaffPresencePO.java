package com.sent.sports.chat.mapper.po;

import java.time.LocalDateTime;

/**
 * 坐席在线状态行（{@code staff_presence}，V9 建表 / 2026-11-28 批次 2 起真正启用）。
 *
 * <p>不加 {@code BasePO}：本表由状态机式的「上报心跳」写入，没有软删与业务创建人语义，
 * 只有 {@code created_at/updated_at} 由数据库默认值维护。</p>
 */
public class StaffPresencePO {

    private Long staffId;
    private String status;
    private Integer maxSessions;
    private Integer activeSessions;
    private LocalDateTime lastHeartbeatAt;

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(Integer maxSessions) {
        this.maxSessions = maxSessions;
    }

    public Integer getActiveSessions() {
        return activeSessions;
    }

    public void setActiveSessions(Integer activeSessions) {
        this.activeSessions = activeSessions;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }
}
