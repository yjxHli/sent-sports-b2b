-- V5 库存（6 表）
CREATE TABLE sku_stock (
    sku_id       BIGINT UNSIGNED NOT NULL,
    location_code VARCHAR(32)    NOT NULL DEFAULT 'DEFAULT',
    physical_qty INT            NOT NULL DEFAULT 0,
    locked_qty   INT            NOT NULL DEFAULT 0,
    pending_qty  INT            NOT NULL DEFAULT 0,
    frozen_qty   INT            NOT NULL DEFAULT 0,
    safety_qty   INT            NOT NULL DEFAULT 0,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT         NULL,
    PRIMARY KEY (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU库存（可用=physical-locked-pending）';

CREATE TABLE stock_flow (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id       BIGINT UNSIGNED NOT NULL,
    biz_type     VARCHAR(16)  NOT NULL,
    change_qty   INT          NOT NULL,
    before_qty   INT          NOT NULL,
    after_qty    INT          NOT NULL,
    biz_no       VARCHAR(48)  NULL,
    operator_id  BIGINT UNSIGNED NULL,
    remark       VARCHAR(255) NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_biz (biz_type, biz_no, sku_id),
    KEY idx_flow_sku (sku_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水（每次变动一条，可重算台账）';

CREATE TABLE stock_inbound (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    inbound_no  VARCHAR(48)  NOT NULL,
    sku_id      BIGINT UNSIGNED NOT NULL,
    qty         INT          NOT NULL,
    reason_type VARCHAR(16)  NOT NULL DEFAULT 'REPLENISH',
    remark      VARCHAR(255) NULL,
    operator_id BIGINT UNSIGNED NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inbound_no (inbound_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库单';

CREATE TABLE stock_loss (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    loss_no     VARCHAR(48)  NOT NULL,
    sku_id      BIGINT UNSIGNED NOT NULL,
    qty         INT          NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    images_json JSON         NULL,
    operator_id BIGINT UNSIGNED NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_loss_no (loss_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报损单';

CREATE TABLE stock_check (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    check_no    VARCHAR(48)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    operator_id BIGINT UNSIGNED NULL,
    approved_by BIGINT UNSIGNED NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_check_no (check_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点单';

CREATE TABLE stock_check_item (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    check_id   BIGINT UNSIGNED NOT NULL,
    sku_id     BIGINT UNSIGNED NOT NULL,
    book_qty   INT          NOT NULL,
    actual_qty INT          NULL,
    diff_qty   INT          NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_check_sku (check_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点明细';
