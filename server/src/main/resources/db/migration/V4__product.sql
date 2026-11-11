-- V4 商品与定价（6 表）
CREATE TABLE product (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_no        VARCHAR(32)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    category_id       BIGINT UNSIGNED NULL,
    brand             VARCHAR(64)  NULL,
    main_image        VARCHAR(512) NULL,
    images_json       JSON         NULL,
    detail_html       MEDIUMTEXT   NULL,
    params_json       JSON         NULL,
    can_customize     TINYINT      NOT NULL DEFAULT 0,
    min_visible_level VARCHAR(32)  NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    published_at      DATETIME(3)  NULL,
    sales_count       BIGINT       NOT NULL DEFAULT 0,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by        BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_no (product_no),
    KEY idx_product_status (status, published_at),
    KEY idx_product_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SPU';

CREATE TABLE product_category (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT UNSIGNED NULL,
    name       VARCHAR(64)  NOT NULL,
    sort       INT          NOT NULL DEFAULT 0,
    icon       VARCHAR(512) NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_category_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类';

CREATE TABLE sku (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    spu_id            BIGINT UNSIGNED NOT NULL,
    sku_no            VARCHAR(48)  NOT NULL,
    barcode           VARCHAR(64)  NULL,
    spec_json         JSON         NULL,
    retail_price_cent BIGINT       NOT NULL DEFAULT 0,
    floor_price_cent  BIGINT       NOT NULL DEFAULT 0,
    cost_price_cent   BIGINT       NOT NULL DEFAULT 0,
    weight_g          INT          NOT NULL DEFAULT 0,
    status            VARCHAR(16)  NOT NULL DEFAULT 'ON',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by        BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_no (sku_no),
    KEY idx_sku_spu (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU';

CREATE TABLE ladder_price (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id     BIGINT UNSIGNED NOT NULL,
    min_qty    INT          NOT NULL,
    max_qty    INT          NULL,
    price_cent BIGINT       NOT NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_ladder_sku (sku_id, min_qty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阶梯价';

CREATE TABLE customer_sku_price (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id BIGINT UNSIGNED NOT NULL,
    sku_id      BIGINT UNSIGNED NOT NULL,
    price_cent  BIGINT       NOT NULL,
    valid_from  DATETIME(3)  NULL,
    valid_to    DATETIME(3)  NULL,
    operator_id BIGINT UNSIGNED NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cus_sku_price (customer_id, sku_id),
    KEY idx_cus_sku_valid (valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一客一价';

CREATE TABLE agreement_price (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id    BIGINT UNSIGNED NOT NULL,
    discount_rate  INT          NOT NULL DEFAULT 100,
    valid_from     DATETIME(3)  NULL,
    valid_to       DATETIME(3)  NULL,
    contract_id    BIGINT UNSIGNED NULL,
    allow_stacking TINYINT      NOT NULL DEFAULT 0,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by     BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_agreement_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='协议价';
