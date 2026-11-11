-- V8 财务（3 表；receivable.order_id 唯一）
CREATE TABLE receivable (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id       BIGINT UNSIGNED NOT NULL,
    customer_id    BIGINT UNSIGNED NOT NULL,
    amount_cent    BIGINT       NOT NULL,
    received_cent  BIGINT       NOT NULL DEFAULT 0,
    due_date       DATE         NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by     BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_receivable_order (order_id),
    KEY idx_receivable_customer (customer_id, status),
    KEY idx_receivable_due (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应收';

CREATE TABLE payment_record (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_no       VARCHAR(48)  NOT NULL,
    customer_id     BIGINT UNSIGNED NOT NULL,
    amount_cent     BIGINT       NOT NULL,
    written_off_cent BIGINT      NOT NULL DEFAULT 0,
    received_date   DATE         NULL,
    bank_serial     VARCHAR(64)  NULL,
    remark          VARCHAR(255) NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    reversed_reason VARCHAR(255) NULL,
    operator_id     BIGINT UNSIGNED NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_no (record_no),
    KEY idx_record_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登记回款';

CREATE TABLE write_off (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    write_off_no      VARCHAR(48)  NOT NULL,
    payment_record_no VARCHAR(48)  NOT NULL,
    receivable_id     BIGINT UNSIGNED NOT NULL,
    customer_id       BIGINT UNSIGNED NOT NULL,
    amount_cent       BIGINT       NOT NULL,
    operator_id       BIGINT UNSIGNED NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by        BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_writeoff_no (write_off_no),
    KEY idx_writeoff_record (payment_record_no),
    KEY idx_writeoff_receivable (receivable_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='核销明细（同一次核销多行共享 write_off_no）';
