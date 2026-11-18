-- ============================================================================
-- V32：客服满意度评价 + 客户头像（2026-11-17 用户要求）
-- 只新增，不修改历史迁移（sent_test 的 Flyway 校验和必须保持稳定）。
-- ============================================================================

-- 1) 客服服务评价：一个会话最多一条有效评价（uk），重复打分按幂等返回原记录
CREATE TABLE IF NOT EXISTS chat_satisfaction (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id  BIGINT UNSIGNED NOT NULL,
    staff_id    BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    /** SATISFIED / NORMAL / UNSATISFIED */
    score       VARCHAR(16)  NOT NULL,
    message_id  VARCHAR(48)  NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_satisfaction_session (session_id),
    KEY idx_satisfaction_staff_day (staff_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服会话满意度评价';

-- 2) 客户头像：只存文件服务 fileId，不存二进制、不存完整 URL
ALTER TABLE customer ADD COLUMN avatar_file_id VARCHAR(128) NULL COMMENT '头像 fileId（/api/files/{id}）' AFTER nickname;
