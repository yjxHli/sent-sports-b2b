package com.sent.sports.customize.service.dto;

/**
 * 超时任务对本轮每张待上传定制单的处置（跨层中立枚举，供 {@code job} 编排使用）。
 */
public enum CustomDraftTimeoutAction {
    /** 已过 72 小时：整单取消并全额退款。 */
    CANCEL,
    /** 到了提醒时点：发一条带剩余时间的站内信。 */
    REMIND,
    /** 还不到提醒时点，或距上次提醒不足 12 小时：本轮不打扰。 */
    NONE
}
