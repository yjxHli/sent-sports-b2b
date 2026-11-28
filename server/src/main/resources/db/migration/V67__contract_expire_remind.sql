-- 合同到期提醒痕迹（P5-43 站内信化）：
-- 没有标记列时定时任务每轮都会给同一份合同重复发站内信；
-- 用递增档位 1=30 天提醒、2=7 天提醒、3=到期当天提醒，条件更新保证并发只发一次。
ALTER TABLE contract
    ADD COLUMN expire_remind_level TINYINT NOT NULL DEFAULT 0 COMMENT '到期提醒档位 0=未提醒 1=30天档 2=7天档 3=到期当天',
    ADD COLUMN expire_reminded_at  DATETIME(3) NULL COMMENT '最近一次合同到期提醒时间';
