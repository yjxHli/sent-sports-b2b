-- V24：补齐客服会话的结束时间。
--
-- 背景：ChatSession 域对象没有 ended_at 字段，仓储 toPO 也不写它，因此历史数据里
-- status='CLOSED' 的行 ended_at 大量为 NULL。工作台「今日完成接待数」按
-- ended_at ∈ [今天 00:00, 明天 00:00) 统计，这些行会被漏掉，表现为接待量恒为 0。
-- 修复两处：
--   1) 应用侧 ChatSessionRepositoryImpl.save() 在结束态补 markEnded()（本次变更）；
--   2) 这里把历史上已结束但缺时间的行用 updated_at 回填，让既有数据同样可统计。
UPDATE chat_session
SET ended_at = COALESCE(updated_at, created_at, NOW(3))
WHERE status = 'CLOSED'
  AND ended_at IS NULL;
