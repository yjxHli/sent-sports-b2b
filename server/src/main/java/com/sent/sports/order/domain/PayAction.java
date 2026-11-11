package com.sent.sports.order.domain;

/** 支付状态迁移动作。 */
public enum PayAction {
    PAY_START,      // 发起支付
    PAY_SUCCESS,    // 回调成功
    PAY_FAIL,       // 支付失败/取消
    PART_REFUND,    // 部分退款
    FULL_REFUND     // 全额退款
}
