package com.sent.sports.refund.domain;

import java.util.List;

/**
 * 退款单聚合（refund 模块）：仅退款（ONLY_REFUND）/ 售后退款等。
 * A2 场景：订单已取消却收到支付回调，生成 ONLY_REFUND（原因 PAID_AFTER_CANCEL）并置 REFUNDING。
 */
public class RefundOrder {

    private final String refundNo;
    private final String orderNo;
    private final long orderId;
    private final long amountCent;
    private final String type;
    private final String reason;
    private RefundStatus status;
    private final long createdAtMillis;
    private int appealCount;
    /** 用户上传的凭证图片 fileId 列表（文件本体在文件服务），审核端据此核验。 */
    private List<String> images = List.of();
    /** 审核结论备注（驳回原因等）；落 refund_order.audit_remark，用户申诉时客服要据此复盘。 */
    private String auditRemark;
    /**
     * 本单退回给用户的抵扣积分数（0 = 没退）。
     * <p>落 {@code refund_order.point_refund}：退款完成后积分的去向必须可追溯，
     * 否则「钱退了、积分到底退没退」只能去翻流水。</p>
     */
    private long pointRefund;

    public RefundOrder(String refundNo, String orderNo, long orderId, long amountCent, String type, String reason,
                      RefundStatus status, long createdAtMillis) {
        this.refundNo = refundNo;
        this.orderNo = orderNo;
        this.orderId = orderId;
        this.amountCent = amountCent;
        this.type = type;
        this.reason = reason;
        this.status = status;
        this.createdAtMillis = createdAtMillis;
        this.appealCount = 0;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public long getOrderId() {
        return orderId;
    }

    public long getAmountCent() {
        return amountCent;
    }

    public String getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public void setStatus(RefundStatus status) {
        this.status = status;
    }

    /** 状态迁移统一入口：非法迁移抛 STATE_CONFLICT(40901)。 */
    public void transit(RefundAction action) {
        this.status = RefundStateMachine.transit(this.status, action);
    }

    /** 状态准入只读判断（不另写状态白名单）。 */
    public boolean canTransit(RefundAction action) {
        return RefundStateMachine.canTransit(this.status, action);
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public int getAppealCount() {
        return appealCount;
    }

    public void setAppealCount(int appealCount) {
        this.appealCount = appealCount;
    }

    /** 恒不为 null，调用方可直接遍历。 */
    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images == null ? List.of() : List.copyOf(images);
    }

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }

    public long getPointRefund() {
        return pointRefund;
    }

    public void setPointRefund(long pointRefund) {
        this.pointRefund = Math.max(0L, pointRefund);
    }
}
