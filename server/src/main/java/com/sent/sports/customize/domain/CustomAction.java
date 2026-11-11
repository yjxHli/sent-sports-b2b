package com.sent.sports.customize.domain;

/** 定制单状态迁移动作（plan §15.2）。 */
public enum CustomAction {
    SUBMIT_DRAFT,       // 上传图稿并提交
    PASS,               // 审核通过
    REJECT,             // 审核驳回
    RESUBMIT,           // 重新上传（驳回后）
    START_PRODUCTION,   // 排产
    FINISH,             // 完工
    CANCEL,             // 用户取消（未排产）
    SHIP                // 随订单行发货
}
