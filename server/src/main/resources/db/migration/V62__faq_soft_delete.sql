-- V62 话术配置软删（2026-11-25）
--
-- 背景：话术配置此前只能「停用」，运营想彻底清掉一条废稿只能手工连库删。
-- 管理端补上删除入口，口径与发票 / 审查记录一致：只打 deleted 标记，不物理删除。
--
-- 为什么不做物理删除：
--   1) 命中次数（hit_count）是运营复盘「这条话术到底有没有用」的唯一依据，
--      删了就再也拿不回来，而误删的概率远高于「省下几百字节」的收益；
--   2) 删除是危险操作，出问题要能查是谁删的、删了哪几条（sys_operation_log 里有 action=DELETE）。
--
-- 默认值必须是 0 且 NOT NULL：写成 1 会让全部存量话术一夜之间从机器人候选集消失，
-- 而现象只是「机器人突然什么都答不上来」，排查成本极高。

ALTER TABLE faq_knowledge
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0
        COMMENT '0=正常 / 1=已删除（软删，命中次数与留痕保留）' AFTER status,
    -- 机器人候选集每次全量读取，按 (deleted, status) 过滤；命中后仍按 id 升序取，
    -- 保证权重并列时永远命中同一条
    ADD KEY idx_faq_deleted (deleted, status, id);
