package com.sent.sports.notify.mapper.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 站内信 {@code user_message} 持久化对象。
 *
 * <p>与客服消息（{@code chat_message}）是两套东西：客服是实时会话，站内信是异步通知，
 * 客户可以在消息中心稍后查看，因此单独建表、单独接口。</p>
 */
@TableName("user_message")
public class UserMessagePO {

    /**
     * 刻意不继承 {@code BasePO}：本表没有 {@code created_by} 列，
     * 继承后 MyBatis-Plus 会按父类字段生成 {@code SELECT created_by}，直接 SQL 报错。
     * 站内信只读 + 标记已读，也不需要自动填充审计字段。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;
    /** ORDER / PROMO / SYSTEM */
    private String msgType;
    private String title;
    private String content;
    /** 点进去要跳的业务类型：ORDER / CUSTOM / COUPON，可空 */
    private String bizType;
    /** 业务单号（订单号等），可空 */
    private String bizNo;
    /** 0 未读 / 1 已读 */
    private Integer readFlag;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Integer getReadFlag() {
        return readFlag;
    }

    public void setReadFlag(Integer readFlag) {
        this.readFlag = readFlag;
    }
}
