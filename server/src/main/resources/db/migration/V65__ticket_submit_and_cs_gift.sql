-- ============================================================================
-- V65：服务工单「暂存 / 提交」+ 回访联系方式手填 + 客服补偿券收口（2026-11-26）
--
-- 1) 工单草稿：客服可以先「暂时保存」去接待别的客户，空闲时回来继续填。
--    草稿允许必填项为空，因此把 V37 的 NOT NULL 放宽为 NULL（空串与 NULL 都按「未填」处理）。
-- 2) 提交状态：只有 SUBMITTED 的工单才进管理端「客服审查」；DRAFT 与未填工单不出现。
--    存量数据一律回填 SUBMITTED，否则历史审查记录会凭空消失。
-- 3) 回访联系方式：不再默认带入客户登录手机号，由客服手填（可能是同事/座机/微信）。
-- 4) 客服补偿券：固定 5 档无门槛（5/8/10/15/20 元），scene=CS_GIFT 与运营券彻底分开。
-- ============================================================================

ALTER TABLE chat_session_note
    MODIFY COLUMN summary   VARCHAR(255)  NULL COMMENT '问题概述（客服手填）',
    MODIFY COLUMN category  VARCHAR(24)   NULL COMMENT '问题分类',
    MODIFY COLUMN issue_info VARCHAR(16)  NULL COMMENT '用户问题信息',
    MODIFY COLUMN result    VARCHAR(16)   NULL COMMENT '处理结果',
    MODIFY COLUMN solution  VARCHAR(1024) NULL COMMENT '解决办法',
    ADD COLUMN followup_contact VARCHAR(64) NULL COMMENT '回访联系方式（客服手填）' AFTER followup_time,
    ADD COLUMN submit_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT 暂存 / SUBMITTED 已提交（只有已提交进客服审查）' AFTER attachments_json,
    ADD COLUMN submitted_at DATETIME(3) NULL COMMENT '最近一次提交时间' AFTER submit_status,
    ADD KEY idx_note_submit (submit_status, submitted_at);

-- 存量工单都是「填完即归档」的语义，一律视为已提交
UPDATE chat_session_note
SET submit_status = 'SUBMITTED',
    submitted_at = COALESCE(updated_at, created_at)
WHERE summary IS NOT NULL AND submit_status <> 'SUBMITTED';

-- 客服补偿券 5 档：全部无门槛、不可领取（只由客服定向发放）、不限量
INSERT IGNORE INTO coupon_template
    (id, template_no, name, amount_cent, threshold_cent, coupon_type, discount_type, discount_rate,
     scope_type, scope_ids, customer_scope, claimable, claim_limit, total_qty, claimed_qty,
     valid_days, scene, status, created_by, deleted)
VALUES
    (31, 'CPT000031', '客服补偿券 5 元',   500, 0, 'NO_THRESHOLD', 'AMOUNT', 100, 'ALL', NULL, 'ALL', 0, 1, -1, 0, 30, 'CS_GIFT', 'ACTIVE', 9001, 0),
    (32, 'CPT000032', '客服补偿券 8 元',   800, 0, 'NO_THRESHOLD', 'AMOUNT', 100, 'ALL', NULL, 'ALL', 0, 1, -1, 0, 30, 'CS_GIFT', 'ACTIVE', 9001, 0),
    (33, 'CPT000033', '客服补偿券 10 元', 1000, 0, 'NO_THRESHOLD', 'AMOUNT', 100, 'ALL', NULL, 'ALL', 0, 1, -1, 0, 30, 'CS_GIFT', 'ACTIVE', 9001, 0),
    (34, 'CPT000034', '客服补偿券 15 元', 1500, 0, 'NO_THRESHOLD', 'AMOUNT', 100, 'ALL', NULL, 'ALL', 0, 1, -1, 0, 30, 'CS_GIFT', 'ACTIVE', 9001, 0),
    (35, 'CPT000035', '客服补偿券 20 元', 2000, 0, 'NO_THRESHOLD', 'AMOUNT', 100, 'ALL', NULL, 'ALL', 0, 1, -1, 0, 30, 'CS_GIFT', 'ACTIVE', 9001, 0);
