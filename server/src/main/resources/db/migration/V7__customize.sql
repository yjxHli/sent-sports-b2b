-- V7 定制与退款/发票/合同（8 表；custom_order.order_item_id 唯一）
CREATE TABLE custom_order (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    custom_no         VARCHAR(48)  NOT NULL,
    order_item_id     BIGINT UNSIGNED NULL,
    customer_id       BIGINT UNSIGNED NOT NULL,
    service_type      VARCHAR(16)  NOT NULL DEFAULT 'ATTACHED',
    print_side        VARCHAR(16)  NOT NULL DEFAULT 'SINGLE',
    qty               INT          NOT NULL,
    fee_cent          BIGINT       NOT NULL DEFAULT 0,
    status            VARCHAR(16)  NOT NULL DEFAULT 'WAIT_DRAFT',
    current_version   INT          NOT NULL DEFAULT 0,
    reject_count      INT          NOT NULL DEFAULT 0,
    review_deadline   DATETIME(3)  NULL,
    reviewer_id       BIGINT UNSIGNED NULL,
    production_start_at DATETIME(3) NULL,
    finished_at       DATETIME(3)  NULL,
    expect_ship_date  DATE         NULL,
    remark            VARCHAR(255) NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by        BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_custom_no (custom_no),
    UNIQUE KEY uk_custom_order_item (order_item_id),
    KEY idx_custom_status (status, review_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定制单';

CREATE TABLE custom_draft (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    custom_order_id BIGINT UNSIGNED NOT NULL,
    version         INT          NOT NULL,
    file_id         BIGINT UNSIGNED NULL,
    file_name       VARCHAR(255) NULL,
    file_size       BIGINT       NULL,
    uploaded_at     DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_draft_version (custom_order_id, version),
    KEY idx_draft_custom (custom_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定制图稿版本';

CREATE TABLE custom_review_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    custom_order_id BIGINT UNSIGNED NOT NULL,
    draft_version   INT          NULL,
    result          VARCHAR(16)  NOT NULL,
    reason_code     VARCHAR(32)  NULL,
    remark          VARCHAR(255) NULL,
    reviewer_id     BIGINT UNSIGNED NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_review_custom (custom_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审图日志';

CREATE TABLE custom_change_request (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    custom_order_id BIGINT UNSIGNED NOT NULL,
    reason          VARCHAR(255) NULL,
    extra_fee_cent  BIGINT       NOT NULL DEFAULT 0,
    new_expect_date DATE         NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_change_custom (custom_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定制变更申请';

CREATE TABLE refund_order (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_no     VARCHAR(48)  NOT NULL,
    order_id      BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NULL,
    type          VARCHAR(16)  NOT NULL,
    amount_cent   BIGINT       NOT NULL,
    point_refund  BIGINT       NOT NULL DEFAULT 0,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    reason_code   VARCHAR(32)  NULL,
    remark        VARCHAR(255) NULL,
    images_json   JSON         NULL,
    applicant_type VARCHAR(16) NULL,
    auditor_id    BIGINT UNSIGNED NULL,
    audit_remark  VARCHAR(255) NULL,
    refunded_at   DATETIME(3)  NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_refund_order (order_id),
    KEY idx_refund_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款/售后单';

CREATE TABLE refund_txn (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_no   VARCHAR(48)  NOT NULL,
    txn_no      VARCHAR(48)  NOT NULL,
    amount_cent BIGINT       NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'CREATED',
    callback_at DATETIME(3)  NULL,
    raw_payload TEXT         NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_txn_no (txn_no),
    KEY idx_refund_txn_refund (refund_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款流水';

CREATE TABLE invoice_request (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id    BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    title       VARCHAR(128) NULL,
    tax_no      VARCHAR(64)  NULL,
    type        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    email       VARCHAR(128) NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'APPLIED',
    invoice_no  VARCHAR(64)  NULL,
    invoice_url VARCHAR(512) NULL,
    issued_at   DATETIME(3)  NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_invoice_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开票申请';

CREATE TABLE contract (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id   BIGINT UNSIGNED NOT NULL,
    contract_no   VARCHAR(48)  NOT NULL,
    sign_date     DATE         NULL,
    expire_date   DATE         NULL,
    discount_rate INT          NOT NULL DEFAULT 100,
    file_url      VARCHAR(512) NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_no (contract_no),
    KEY idx_contract_customer (customer_id),
    KEY idx_contract_expire (expire_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同';
