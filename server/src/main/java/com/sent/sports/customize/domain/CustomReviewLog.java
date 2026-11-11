package com.sent.sports.customize.domain;

/**
 * 审图日志实体（Phase 10）：一次审核动作生成一条只读记录，落 {@code custom_review_log}。
 * <p>与 {@link CustomDraft} 一样「只 insert 不 update」；V13 的唯一键
 * {@code uk_review_once(custom_order_id, draft_version, result)} 保证同一版本同一结果只记一次，
 * 符合项目「流水必须记，防重只用 DB 唯一索引」的口径。纯 Java POJO，无 Spring 依赖。</p>
 */
public class CustomReviewLog {

    private final String customOrderId;
    private final int draftVersion;
    private final String result;
    private final String reasonCode;
    private final String remark;
    private final Long reviewerId;
    private final long createdAtMillis;

    public CustomReviewLog(String customOrderId, int draftVersion, String result,
                           String reasonCode, String remark, Long reviewerId, long createdAtMillis) {
        this.customOrderId = customOrderId;
        this.draftVersion = draftVersion;
        this.result = result;
        this.reasonCode = reasonCode;
        this.remark = remark;
        this.reviewerId = reviewerId;
        this.createdAtMillis = createdAtMillis;
    }

    public String getCustomOrderId() {
        return customOrderId;
    }

    public int getDraftVersion() {
        return draftVersion;
    }

    public String getResult() {
        return result;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getRemark() {
        return remark;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
}
