package com.sent.sports.product.mapper.po;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDateTime;

/**
 * 秒杀活动表 {@code flash_sale_activity}（V54）。
 * <p>{@code status} 只是运营开关（DRAFT/ACTIVE/OFFLINE）；「进行中 / 已结束」是
 * 结合 {@code startAt/endAt} 现算的展示态，不落库，避免定时任务与状态不一致。</p>
 */
@TableName("flash_sale_activity")
public class FlashSaleActivityPO extends BasePO {

    private String activityNo;
    private String title;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private String remark;

    @TableLogic
    private Integer deleted;

    public String getActivityNo() { return activityNo; }
    public void setActivityNo(String activityNo) { this.activityNo = activityNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
