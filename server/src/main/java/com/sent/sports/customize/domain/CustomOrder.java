package com.sent.sports.customize.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

/**
 * 定制单聚合（customize 模块，P3-25）：承载定制单主状态、当前图稿版本号、驳回次数与审核截止时间。
 * 状态迁移统一走 {@link CustomStateMachine}（集中、非法迁移抛 STATE_CONFLICT）。
 * 纯 Java 领域对象，无 Spring 依赖。
 */
public class CustomOrder {

    private final String customOrderId;
    private CustomStatus status;
    private int currentVersion;
    private int rejectCount;
    private int lockedVersion;
    private long reviewDeadlineMillis;
    private long startedAtMillis;
    private long finishedAtMillis;

    public CustomOrder(String customOrderId, CustomStatus status, int currentVersion, int rejectCount, long reviewDeadlineMillis) {
        this.customOrderId = customOrderId;
        this.status = status;
        this.currentVersion = currentVersion;
        this.rejectCount = rejectCount;
        this.lockedVersion = 0;
        this.reviewDeadlineMillis = reviewDeadlineMillis;
        this.startedAtMillis = 0L;
        this.finishedAtMillis = 0L;
    }

    public String getCustomOrderId() {
        return customOrderId;
    }

    public CustomStatus getStatus() {
        return status;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public int getRejectCount() {
        return rejectCount;
    }

    public int getLockedVersion() {
        return lockedVersion;
    }

    public void setLockedVersion(int lockedVersion) {
        this.lockedVersion = lockedVersion;
    }

    public long getReviewDeadlineMillis() {
        return reviewDeadlineMillis;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public void setStartedAtMillis(long startedAtMillis) {
        this.startedAtMillis = startedAtMillis;
    }

    public long getFinishedAtMillis() {
        return finishedAtMillis;
    }

    public void setFinishedAtMillis(long finishedAtMillis) {
        this.finishedAtMillis = finishedAtMillis;
    }

    /** 版本号 +1（每次提交图稿生成新版本）。 */
    public void bumpVersion() {
        this.currentVersion += 1;
    }

    /** 驳回次数 +1（审核驳回时由审核用例调用）。 */
    public void incrementRejectCount() {
        this.rejectCount += 1;
    }

    public void setReviewDeadlineMillis(long reviewDeadlineMillis) {
        this.reviewDeadlineMillis = reviewDeadlineMillis;
    }

    /** 状态迁移（集中，非法迁移抛 STATE_CONFLICT）。 */
    public void transit(CustomAction action) {
        if (action == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        this.status = CustomStateMachine.transit(this.status, action, this.rejectCount);
    }
}
