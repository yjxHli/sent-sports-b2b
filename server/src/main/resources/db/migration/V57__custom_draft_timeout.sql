-- V57 定制图稿上传超时（2026-11-23 用户口径）
-- 规则：订单支付后 72 小时内未上传图稿 → 自动取消订单并全额退款（无需人工审核），
--       积分与优惠券一并返还；期间每 12 小时发一次站内信提醒，并告知剩余时间。
-- 本迁移只加「提醒痕迹」两列：没有它，定时任务每轮都会重复推送同一条提醒。
ALTER TABLE custom_order
    ADD COLUMN draft_remind_count   INT         NOT NULL DEFAULT 0 COMMENT '图稿上传提醒已发送次数',
    ADD COLUMN last_draft_remind_at DATETIME(3) NULL COMMENT '最近一次图稿上传提醒时间（每 12 小时最多一次）',
    ADD KEY idx_custom_draft_remind (status, last_draft_remind_at);
