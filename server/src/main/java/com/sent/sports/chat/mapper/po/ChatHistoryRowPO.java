package com.sent.sports.chat.mapper.po;

import java.time.LocalDateTime;

/** 客户历史服务记录投影（会话 + 接待人 + 小结结论）。 */
public class ChatHistoryRowPO {

    private Long sessionId;
    private Long staffId;
    private String staffName;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String closeReason;
    private String result;
    private Integer messageCount;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getStaffId() { return staffId; }
    public void setStaffId(Long staffId) { this.staffId = staffId; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
}
