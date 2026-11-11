-- V3 客户与会员（9 表）
CREATE TABLE customer (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_no    VARCHAR(32)  NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    password_hash  VARCHAR(128) NULL,
    nickname       VARCHAR(64)  NULL,
    email          VARCHAR(128) NULL,
    type           VARCHAR(16)  NOT NULL DEFAULT 'PERSON',
    level_code     VARCHAR(32)  NULL,
    growth_value   BIGINT       NOT NULL DEFAULT 0,
    points_balance BIGINT       NOT NULL DEFAULT 0,
    owner_staff_id BIGINT UNSIGNED NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    token_version  BIGINT       NOT NULL DEFAULT 0,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by     BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_no (customer_no),
    UNIQUE KEY uk_customer_phone (phone),
    KEY idx_customer_owner (owner_staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户';

CREATE TABLE enterprise_profile (
    customer_id        BIGINT UNSIGNED NOT NULL,
    company_name       VARCHAR(128) NOT NULL,
    license_no         VARCHAR(64)  NULL,
    license_url        VARCHAR(512) NULL,
    invoice_title      VARCHAR(128) NULL,
    tax_no             VARCHAR(64)  NULL,
    credit_limit_cent  BIGINT       NOT NULL DEFAULT 0,
    credit_used_cent   BIGINT       NOT NULL DEFAULT 0,
    payment_term_days  INT          NOT NULL DEFAULT 0,
    credit_expire_at   DATETIME(3)  NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by         BIGINT       NULL,
    PRIMARY KEY (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业档案';

CREATE TABLE customer_address (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id BIGINT UNSIGNED NOT NULL,
    receiver   VARCHAR(64)  NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    province   VARCHAR(64)  NULL,
    city       VARCHAR(64)  NULL,
    district   VARCHAR(64)  NULL,
    detail     VARCHAR(255) NOT NULL,
    is_default TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_addr_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址';

CREATE TABLE customer_owner_log (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id   BIGINT UNSIGNED NOT NULL,
    from_staff_id BIGINT UNSIGNED NULL,
    to_staff_id   BIGINT UNSIGNED NULL,
    reason        VARCHAR(255) NULL,
    operator_id   BIGINT UNSIGNED NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_ownerlog_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户归属变更日志';

CREATE TABLE member_level (
    code            VARCHAR(32) NOT NULL,
    name            VARCHAR(64) NOT NULL,
    growth_threshold BIGINT      NOT NULL DEFAULT 0,
    discount_rate   INT         NOT NULL DEFAULT 100,
    point_rate      INT         NOT NULL DEFAULT 100,
    priority        INT         NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT      NULL,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员等级';

CREATE TABLE growth_log (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id  BIGINT UNSIGNED NOT NULL,
    change_value BIGINT      NOT NULL,
    balance_after BIGINT     NOT NULL,
    source_type  VARCHAR(16) NOT NULL,
    source_id    VARCHAR(64) NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT      NULL,
    PRIMARY KEY (id),
    KEY idx_growth_customer (customer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成长值流水';

CREATE TABLE point_log (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id  BIGINT UNSIGNED NOT NULL,
    change_value BIGINT      NOT NULL,
    balance_after BIGINT     NOT NULL,
    type         VARCHAR(16) NOT NULL,
    source_type  VARCHAR(16) NULL,
    source_id    VARCHAR(64) NULL,
    expire_at    DATETIME(3) NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT      NULL,
    PRIMARY KEY (id),
    KEY idx_point_customer (customer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水';

CREATE TABLE customer_follow_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id BIGINT UNSIGNED NOT NULL,
    staff_id    BIGINT UNSIGNED NULL,
    content     VARCHAR(1024) NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT      NULL,
    PRIMARY KEY (id),
    KEY idx_follow_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户跟进';

CREATE TABLE arrival_subscription (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id BIGINT UNSIGNED NOT NULL,
    sku_id      BIGINT UNSIGNED NOT NULL,
    notified    TINYINT     NOT NULL DEFAULT 0,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arrival (customer_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='到货订阅';
