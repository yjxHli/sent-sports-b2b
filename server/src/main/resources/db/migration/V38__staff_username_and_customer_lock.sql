-- ============================================================================
-- V38：登录名独立持久化 + 客户账号失败锁定列（2026-11-18）
-- 背景：认证仓储改为直连 MySQL，登录名必须落库；工号 staff_no 是完全独立的业务字段，
--       现有 9001/9003~9007 的工号一律不改动。
-- 表数仍为 60（只加列/索引，不加表）。
-- ============================================================================

-- 1) 员工登录名列（与 staff_no 工号分离）
ALTER TABLE sys_staff
    ADD COLUMN username VARCHAR(64) NULL COMMENT '登录名（与工号 staff_no 分离）' AFTER staff_no;
ALTER TABLE sys_staff ADD UNIQUE KEY uk_staff_username (username);

-- 2) 回填已有演示账号的登录名；只写 username，绝不碰 staff_no
UPDATE sys_staff SET username = 'admin'     WHERE id = 9001 AND staff_no = 'ADMIN001';
UPDATE sys_staff SET username = 'service'   WHERE id = 9003 AND staff_no = 'SERVICE001';
UPDATE sys_staff SET username = 'sales_mgr' WHERE id = 9004 AND staff_no = 'SALESMGR001';
UPDATE sys_staff SET username = 'sales'     WHERE id = 9005 AND staff_no = 'SALES001';
UPDATE sys_staff SET username = 'finance'   WHERE id = 9006 AND staff_no = 'FIN001';
UPDATE sys_staff SET username = 'warehouse' WHERE id = 9007 AND staff_no = 'WH001';
UPDATE sys_staff SET username = 'chat_mgr'  WHERE id = 9008 AND staff_no = 'CHATMG001';

-- 3) 客户账号：登录失败计数 / 锁定截止时间（与 sys_staff 同名列同口径）
ALTER TABLE customer
    ADD COLUMN fail_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数' AFTER token_version,
    ADD COLUMN locked_until DATETIME(3) NULL COMMENT '锁定截止时间' AFTER fail_count;
