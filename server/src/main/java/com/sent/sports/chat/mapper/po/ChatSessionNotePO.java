package com.sent.sports.chat.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDateTime;

/** 服务工单记录表行（V37 建表，V61 扩展工单字段）。 */
@TableName("chat_session_note")
public class ChatSessionNotePO extends BasePO {

    private Long sessionId;
    private Long customerId;
    private Long staffId;
    private String summary;
    private String category;
    private String subCategory;
    private String customerDemand;
    private String verifiedInfo;
    private String issueInfo;
    private String result;
    private String solution;
    private Boolean needFollowup;
    private LocalDateTime followupTime;
    /** 回访联系方式由客服手填，不再默认取客户登录手机号（V65）。 */
    private String followupContact;
    private String attachmentsJson;
    /** DRAFT 暂存 / SUBMITTED 已提交；只有 SUBMITTED 进管理端客服审查（V65）。 */
    private String submitStatus;
    private LocalDateTime submittedAt;

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
    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    public String getCustomerDemand() { return customerDemand; }
    public void setCustomerDemand(String customerDemand) { this.customerDemand = customerDemand; }
    public String getVerifiedInfo() { return verifiedInfo; }
    public void setVerifiedInfo(String verifiedInfo) { this.verifiedInfo = verifiedInfo; }
    public String getIssueInfo() { return issueInfo; }
    public void setIssueInfo(String issueInfo) { this.issueInfo = issueInfo; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getSolution() { return solution; }
    public void setSolution(String solution) { this.solution = solution; }
    public Boolean getNeedFollowup() { return needFollowup; }
    public void setNeedFollowup(Boolean needFollowup) { this.needFollowup = needFollowup; }
    public LocalDateTime getFollowupTime() { return followupTime; }
    public void setFollowupTime(LocalDateTime followupTime) { this.followupTime = followupTime; }
    public String getFollowupContact() { return followupContact; }
    public void setFollowupContact(String followupContact) { this.followupContact = followupContact; }
    public String getAttachmentsJson() { return attachmentsJson; }
    public void setAttachmentsJson(String attachmentsJson) { this.attachmentsJson = attachmentsJson; }
    public String getSubmitStatus() { return submitStatus; }
    public void setSubmitStatus(String submitStatus) { this.submitStatus = submitStatus; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
