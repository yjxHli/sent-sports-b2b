-- V39 客服审查软删标记：只从审查列表隐藏，不改写会话/消息/小结历史
ALTER TABLE chat_session
    ADD COLUMN audit_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '客服审查软删标记';
