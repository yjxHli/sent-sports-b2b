-- V17：业务单号持久化序列
-- 背景：IdGen 原为进程内 AtomicLong，应用重启后从 1 重新计数，与库中已存在的
--       SO/PR/RF/CU 单号冲突（下单会命中历史单、支付金额校验直接 40001）。
--       改为 DB 序列：重启后续增，保证单号全局唯一。
-- 初始值 1000：历史演示数据单号均 < 000100，取 1000 起跳彻底避开既有单号。
CREATE TABLE IF NOT EXISTS sys_sequence (
    seq_name   VARCHAR(32) NOT NULL COMMENT '序列名（BIZ_NO = 业务单号统一序列）',
    seq_value  BIGINT      NOT NULL DEFAULT 0 COMMENT '当前值',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后变更时间',
    PRIMARY KEY (seq_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '业务单号序列（重启后延续）';

INSERT INTO sys_sequence (seq_name, seq_value)
VALUES ('BIZ_NO', 1000)
ON DUPLICATE KEY UPDATE seq_value = GREATEST(seq_value, 1000);
