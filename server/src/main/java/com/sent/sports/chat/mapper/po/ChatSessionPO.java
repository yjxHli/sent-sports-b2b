package com.sent.sports.chat.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDateTime;

/**
 * 客服会话 {@code chat_session} 持久化对象（P5-18）。
 * <p>{@code seq} 是消息序号分配器：分配走 {@code UPDATE ... SET seq = seq + 1} 的原子自增，
 * 保证同一会话内消息序号严格递增且不重复（{@code uk_msg_seq} 兜底）。
 */
@TableName("chat_session")
public class ChatSessionPO extends BasePO {

    private Long customerId;
    private Long staffId;
    private String status;
    private String sourceType;
    private String sourceId;
    private Integer vip;
    private Integer priority;
    private Long seq;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime lastMsgAt;
    private String closeReason;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public Integer getVip() {
        return vip;
    }

    public void setVip(Integer vip) {
        this.vip = vip;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public LocalDateTime getLastMsgAt() {
        return lastMsgAt;
    }

    public void setLastMsgAt(LocalDateTime lastMsgAt) {
        this.lastMsgAt = lastMsgAt;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }
}
