-- 客服并发接待上限默认值 10 → 3（与工作台同时最多 3 个会话 Tab 对齐）
-- 存量行同步收紧到 3：超过 3 的历史配置会让「关一个补一个」永远补不满，队列被卡死。
ALTER TABLE staff_presence
    ALTER COLUMN max_sessions SET DEFAULT 3;

UPDATE staff_presence SET max_sessions = 3 WHERE max_sessions > 3;
