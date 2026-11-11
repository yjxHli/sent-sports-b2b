package com.sent.sports.customize.mapper.po;

import java.time.LocalDateTime;

/**
 * 用户端「我的定制单」列表视图对象（收口阶段）。
 * <p>由 {@code CustomOrderMapper.selectMyPage} 经 JOIN 查询装配：
 * {@code custom_order} 主表 + {@code order_item}/{@code orders} 取订单状态 + {@code custom_draft} 取最新图稿版本。
 * 非 {@code @TableName} 实体，仅作结果映射目标，不参与写操作。</p>
 */
public class CustomOrderListItemPO {

    private String customOrderId;
    private String status;
    private Integer rejectCount;
    private Integer version;
    private String orderStatus;
    private LocalDateTime reviewDeadline;

    public String getCustomOrderId() {
        return customOrderId;
    }

    public void setCustomOrderId(String customOrderId) {
        this.customOrderId = customOrderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRejectCount() {
        return rejectCount;
    }

    public void setRejectCount(Integer rejectCount) {
        this.rejectCount = rejectCount;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public LocalDateTime getReviewDeadline() {
        return reviewDeadline;
    }

    public void setReviewDeadline(LocalDateTime reviewDeadline) {
        this.reviewDeadline = reviewDeadline;
    }
}
