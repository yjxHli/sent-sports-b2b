-- V59 智能客服：机器人知识库 + 会话接待模式（2026-11-24）
-- 背景：客户进线先由「智能助手小森」接待（session_type=BOT），命中知识库直接答；
--       客户发「转人工」或机器人答不上来时切 HUMAN 进排队队列，由人工无缝接上。
-- 注意：老会话一律按 HUMAN 回填（默认值），不能让既有会话突然被机器人接管。

CREATE TABLE IF NOT EXISTS faq_knowledge (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    keywords      VARCHAR(255)    NOT NULL COMMENT '关键词，英文逗号分隔（命中任一即算匹配）',
    reply_content VARCHAR(500)    NOT NULL COMMENT '命中后机器人回复的原文',
    hit_count     INT             NOT NULL DEFAULT 0 COMMENT '命中次数（运营据此优化知识库）',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT          NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服机器人知识库';

ALTER TABLE chat_session
    ADD COLUMN session_type VARCHAR(16) NOT NULL DEFAULT 'HUMAN'
        COMMENT 'BOT 机器人接待 / HUMAN 人工接待';

INSERT INTO faq_knowledge (keywords, reply_content, hit_count) VALUES
    ('发货,物流,快递,几天到,多久到', '亲，我们默认发顺丰，48小时内发货哦~', 0),
    ('定制,图稿,印号,队徽', '定制商品请先在订单详情上传图稿，客服会在24小时内审核~', 0),
    ('发票,开票,专票', '可以开增值税专用发票，在「我的订单-申请开票」提交，财务1个工作日内开出~', 0),
    ('退货,退款,退换,换货', '支持7天无理由退换，在订单详情点「申请售后」就行，我们承担退货运费~', 0),
    ('尺码,大小,码数', '球衣为欧码偏大，建议比平时小一码；详情页有尺码对照表可以参考哦~', 0);
