-- V58 搜索合规词库（2026-11-24）
-- 商品搜索/联想与商品评价共用同一棵 DFA 词树；这三个词是搜索合规场景的必拦项，
-- 落库后由 SensitiveWordBootstrap 在启动时重载，管理端改词依然即时生效。
INSERT IGNORE INTO sys_sensitive_word (word, category, remark) VALUES
    ('毒品', 'ILLEGAL', '搜索合规：违法违禁品'),
    ('冰毒', 'ILLEGAL', '搜索合规：违法违禁品'),
    ('枪支', 'ILLEGAL', '搜索合规：违法违禁品');
