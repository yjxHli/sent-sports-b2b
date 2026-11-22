-- V54 商品详情富内容 + 秒杀活动价
--
-- 背景：V4 建表时已留出 images_json / detail_html / params_json / spec_json 四列，
-- 但管理端从未维护、用户端从未渲染。本迁移补齐真正缺失的列，并新增秒杀活动两张表。
--
-- 秒杀口径（详见 docs/product-detail-taobao-refactor-plan-2026-11-21.md §5）：
--   * 活动生效 = status='ACTIVE' AND now BETWEEN start_at AND end_at（不依赖定时任务改状态）
--   * 秒杀价作为 PriceSource.FLASH_SALE 进入定价引擎，经 ProductPriceRepository 注入 PriceContext，
--     因此购物车预览与下单金额同时生效
--   * 不做活动限量配额：库存仍由 sku_stock 约束，避免超卖并发风险

ALTER TABLE product
    ADD COLUMN subtitle           VARCHAR(255) NULL COMMENT '卖点副标题' AFTER title,
    ADD COLUMN detail_images_json JSON         NULL COMMENT '详情长图 fileId 数组' AFTER images_json,
    ADD COLUMN service_tags_json  JSON         NULL COMMENT '服务承诺标签数组' AFTER params_json;

ALTER TABLE sku
    ADD COLUMN image_file_id VARCHAR(512) NULL COMMENT '款式图 fileId' AFTER spec_json;

CREATE TABLE flash_sale_activity (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    activity_no VARCHAR(32)  NOT NULL,
    title       VARCHAR(128) NOT NULL,
    start_at    DATETIME(3)  NOT NULL,
    end_at      DATETIME(3)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/OFFLINE',
    remark      VARCHAR(255) NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flash_activity_no (activity_no),
    KEY idx_flash_status_time (status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动';

CREATE TABLE flash_sale_item (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    activity_id      BIGINT UNSIGNED NOT NULL,
    sku_id           BIGINT UNSIGNED NOT NULL,
    flash_price_cent BIGINT       NOT NULL,
    sort             INT          NOT NULL DEFAULT 0,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by       BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flash_activity_sku (activity_id, sku_id),
    KEY idx_flash_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动商品';
