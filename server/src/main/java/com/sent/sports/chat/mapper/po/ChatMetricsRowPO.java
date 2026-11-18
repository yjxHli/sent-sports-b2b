package com.sent.sports.chat.mapper.po;

/** 客服指标聚合行：每位客服在时间区间内的接待 / 评价 / 解决 / 时长原始计数。 */
public class ChatMetricsRowPO {

    private long staffId;
    private String staffName;
    private int closedCount;
    private int ratedCount;
    private int satisfiedCount;
    private int resolvedCount;
    private long avgDurationSeconds;

    public long getStaffId() {
        return staffId;
    }

    public void setStaffId(long staffId) {
        this.staffId = staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public int getClosedCount() {
        return closedCount;
    }

    public void setClosedCount(int closedCount) {
        this.closedCount = closedCount;
    }

    public int getRatedCount() {
        return ratedCount;
    }

    public void setRatedCount(int ratedCount) {
        this.ratedCount = ratedCount;
    }

    public int getSatisfiedCount() {
        return satisfiedCount;
    }

    public void setSatisfiedCount(int satisfiedCount) {
        this.satisfiedCount = satisfiedCount;
    }

    public int getResolvedCount() {
        return resolvedCount;
    }

    public void setResolvedCount(int resolvedCount) {
        this.resolvedCount = resolvedCount;
    }

    public long getAvgDurationSeconds() {
        return avgDurationSeconds;
    }

    public void setAvgDurationSeconds(long avgDurationSeconds) {
        this.avgDurationSeconds = avgDurationSeconds;
    }
}
