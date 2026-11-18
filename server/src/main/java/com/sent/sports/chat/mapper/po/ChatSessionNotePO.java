package com.sent.sports.chat.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/** 会话小结表行（V37）。 */
@TableName("chat_session_note")
public class ChatSessionNotePO extends BasePO {

    private Long sessionId;
    private Long customerId;
    private Long staffId;
    private String summary;
    private String category;
    private String issueInfo;
    private String result;
    private String solution;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getStaffId() { return staffId; }
    public void setStaffId(Long staffId) { this.staffId = staffId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getIssueInfo() { return issueInfo; }
    public void setIssueInfo(String issueInfo) { this.issueInfo = issueInfo; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getSolution() { return solution; }
    public void setSolution(String solution) { this.solution = solution; }
}
