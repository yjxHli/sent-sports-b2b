package com.sent.sports.order.domain;

/** 订单状态迁移动作（plan §15.1）。 */
public enum OrderAction {
    PAY,                 // 支付回调成功
    USER_CANCEL,         // 用户取消（待付款）
    TIMEOUT_CANCEL,      // 超时自动取消
    LATE_PAY_CALLBACK,   // 迟到回调（订单已取消，保持取消并生成退款单）
    SHIP_PARTIAL,        // 待发货→部分发货
    SHIP_FULL,           // 发货（已发完）→ 待收货
    SHIP_CONTINUE,       // 部分发货→继续部分发货
    CONFIRM_RECEIVE,     // 全部包裹确认收货 → 已完成
    ADMIN_CLOSE,         // 管理员关闭（未发部分退款）
    REFUND_DONE          // 退款完成 → 已关闭
}
