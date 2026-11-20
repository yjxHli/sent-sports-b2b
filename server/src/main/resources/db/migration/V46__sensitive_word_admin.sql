-- V46 违禁词库上管理端（2026-11-19）
-- 背景：词库原先写死在 classpath 的 sensitive_words.txt，运营想加一个广告词就得发版。
-- 现在落库 + 管理端维护，保存后由 SensitiveWordBootstrap/Service 热重载 SensitiveWordFilter，无需重启。
-- 设计取舍：
--   1) 唯一键收在 word 上：同一个词不允许重复录入（前端提示「已存在」，后端兜 DuplicateKey）。
--   2) 删除是物理删除：这是配置类数据，不涉及金额/流水，留痕由 sys_operation_log 负责，
--      软删反而会让「删掉再加回来」撞唯一键。
--   3) enabled 是热开关：临时停用某个词不用删，改回 1 立刻生效。
CREATE TABLE sys_sensitive_word (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    word       VARCHAR(64)  NOT NULL COMMENT '违禁词（只收完整词组，不收单字）',
    category   VARCHAR(24)  NOT NULL DEFAULT 'OTHER' COMMENT 'PROFANITY 辱骂 / AD 广告导流 / ILLEGAL 违法违禁 / OTHER 其他',
    enabled    TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用（参与匹配） / 0 停用',
    remark     VARCHAR(255) NULL COMMENT '备注：命中后怎么处理 / 来源',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT       NULL COMMENT '录入人（sys_staff.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sensitive_word (word),
    KEY idx_sensitive_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天/昵称违禁词库';

-- 种子：与 classpath 的 sensitive_words.txt 保持一致（首次启动即由 DB 接管）
-- 「测试违禁词 / 傻X / 加V」三个词供 E2E 断言，别删。
INSERT IGNORE INTO sys_sensitive_word (word, category, remark) VALUES
    ('傻X',       'PROFANITY', '脏话/辱骂'),
    ('傻逼',      'PROFANITY', '脏话/辱骂'),
    ('操你妈',    'PROFANITY', '脏话/辱骂（不收单字「操」，避免误杀「操作」）'),
    ('草泥马',    'PROFANITY', '脏话/辱骂'),
    ('他妈的',    'PROFANITY', '脏话/辱骂'),
    ('狗日的',    'PROFANITY', '脏话/辱骂'),
    ('贱人',      'PROFANITY', '脏话/辱骂'),
    ('去死吧',    'PROFANITY', '脏话/辱骂'),
    ('神经病',    'PROFANITY', '脏话/辱骂'),
    ('垃圾东西',  'PROFANITY', '脏话/辱骂'),
    ('加V',       'AD',        '广告导流（E2E 断言用）'),
    ('加微信',    'AD',        '广告导流'),
    ('加我微信',  'AD',        '广告导流'),
    ('私聊下单',  'AD',        '广告导流'),
    ('微信转账',  'AD',        '站外交易'),
    ('代开发票',  'AD',        '违规交易'),
    ('刷单',      'AD',        '刷单炒信'),
    ('刷好评',    'AD',        '刷单炒信'),
    ('返现私聊',  'AD',        '诱导好评'),
    ('低价出号',  'AD',        '账号买卖'),
    ('反动',      'ILLEGAL',   '违法/违禁内容'),
    ('邪教',      'ILLEGAL',   '违法/违禁内容'),
    ('暴恐',      'ILLEGAL',   '违法/违禁内容'),
    ('恐怖袭击',  'ILLEGAL',   '违法/违禁内容'),
    ('制毒',      'ILLEGAL',   '违法/违禁内容'),
    ('枪支买卖',  'ILLEGAL',   '违法/违禁内容'),
    ('赌博网站',  'ILLEGAL',   '违法/违禁内容'),
    ('办证刻章',  'ILLEGAL',   '违法/违禁内容'),
    ('测试违禁词', 'OTHER',    '联调/E2E 断言专用，勿删');

-- 权限点：菜单「违禁词库」+ 动作「违禁词维护」。
-- 超管（角色 1）持 '*' 通配已自动覆盖；这里建出来是为了让「角色权限矩阵」能给自定义角色勾选。
INSERT IGNORE INTO sys_permission (code, name, module, type) VALUES
    ('system:sensitive',        '违禁词库',   'SYSTEM', 'MENU'),
    ('system:sensitive:manage', '违禁词维护', 'SYSTEM', 'ACTION');
