package com.sent.sports.chat.mapper.po;

import java.time.LocalDateTime;

/** 客服审查列表投影。 */
public class ChatAuditRowPO {

    private Long sessionId;
    private Long customerId;
    private Long staffId;
    private String staffName;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String summary;
    private String result;
    /** 工单提交状态（V65）：审查列表只查 SUBMITTED，字段保留便于前端展示与排查。 */
    private String submitStatus;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getStaffId() { return staffId; }
    public void setStaffId(Long staffId) { this.staffId = staffId; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getSubmitStatus() { return submitStatus; }
    public void setSubmitStatus(String submitStatus) { this.submitStatus = submitStatus; }
}
