-- V6 购物车、订单与支付（7 表；orders.idem_key 唯一索引为幂等基石）
CREATE TABLE cart_item (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id         BIGINT UNSIGNED NOT NULL,
    sku_id              BIGINT UNSIGNED NOT NULL,
    qty                 INT          NOT NULL DEFAULT 1,
    customize_flag      TINYINT      NOT NULL DEFAULT 0,
    custom_config_json  JSON         NULL,
    selected            TINYINT      NOT NULL DEFAULT 1,
    price_snapshot_cent BIGINT       NOT NULL DEFAULT 0,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by          BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_item (customer_id, sku_id, customize_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车';

CREATE TABLE orders (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no              VARCHAR(32)  NOT NULL,
    customer_id           BIGINT UNSIGNED NOT NULL,
    customer_type         VARCHAR(16)  NULL,
    owner_staff_id        BIGINT UNSIGNED NULL,
    status                VARCHAR(16)  NOT NULL DEFAULT 'WAIT_PAY',
    pay_status            VARCHAR(16)  NOT NULL DEFAULT 'UNPAID',
    custom_status         VARCHAR(16)  NULL,
    after_sale_status     VARCHAR(16)  NULL,
    goods_amount_cent     BIGINT       NOT NULL DEFAULT 0,
    custom_fee_cent       BIGINT       NOT NULL DEFAULT 0,
    discount_cent         BIGINT       NOT NULL DEFAULT 0,
    freight_cent          BIGINT       NOT NULL DEFAULT 0,
    point_deduct_cent     BIGINT       NOT NULL DEFAULT 0,
    payable_cent          BIGINT       NOT NULL DEFAULT 0,
    paid_cent             BIGINT       NOT NULL DEFAULT 0,
    received_cent         BIGINT       NOT NULL DEFAULT 0,
    address_snapshot_json JSON         NULL,
    invoice_flag          TINYINT      NOT NULL DEFAULT 0,
    remark                VARCHAR(255) NULL,
    expire_at             DATETIME(3)  NULL,
    paid_at               DATETIME(3)  NULL,
    completed_at          DATETIME(3)  NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    idem_key              VARCHAR(64)  NULL,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by            BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_idem (idem_key),
    KEY idx_order_customer (customer_id, created_at),
    KEY idx_order_status (status, expire_at),
    KEY idx_order_owner (owner_staff_id),
    KEY idx_order_pay (pay_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单';

CREATE TABLE order_item (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id            BIGINT UNSIGNED NOT NULL,
    sku_id              BIGINT UNSIGNED NOT NULL,
    spu_title_snap      VARCHAR(255) NULL,
    spec_snap           VARCHAR(255) NULL,
    qty                 INT          NOT NULL,
    unit_price_cent     BIGINT       NOT NULL,
    price_source        VARCHAR(32)  NULL,
    price_rule_id       VARCHAR(48)  NULL,
    amount_cent         BIGINT       NOT NULL,
    discount_alloc_cent BIGINT       NOT NULL DEFAULT 0,
    shipped_qty         INT          NOT NULL DEFAULT 0,
    custom_flag         TINYINT      NOT NULL DEFAULT 0,
    custom_fee_cent     BIGINT       NOT NULL DEFAULT 0,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by          BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_item_order (order_id),
    KEY idx_item_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单行（事实快照）';

CREATE TABLE shipment (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shipment_no  VARCHAR(48)  NOT NULL,
    order_id     BIGINT UNSIGNED NOT NULL,
    carrier      VARCHAR(64)  NULL,
    tracking_no  VARCHAR(64)  NULL,
    shipped_at   DATETIME(3)  NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'SHIPPED',
    operator_id  BIGINT UNSIGNED NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_no (shipment_no),
    KEY idx_shipment_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='包裹';

CREATE TABLE shipment_item (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shipment_id   BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NOT NULL,
    qty           INT          NOT NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_shipitem_shipment (shipment_id),
    KEY idx_shipitem_item (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='包裹明细';

CREATE TABLE order_status_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id    BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(16)  NULL,
    to_status   VARCHAR(16)  NOT NULL,
    dimension   VARCHAR(16)  NOT NULL DEFAULT 'MAIN',
    operator_id BIGINT UNSIGNED NULL,
    remark      VARCHAR(255) NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_statuslog_order (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单状态流水';

CREATE TABLE payment_txn (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    txn_no      VARCHAR(48)  NOT NULL,
    order_no    VARCHAR(32)  NOT NULL,
    amount_cent BIGINT       NOT NULL,
    channel     VARCHAR(32)  NULL,
    channel_trade_no VARCHAR(64) NULL,
    idem_key    VARCHAR(64)  NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'CREATED',
    request_at  DATETIME(3)  NULL,
    callback_at DATETIME(3)  NULL,
    raw_payload TEXT         NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_txn_no (txn_no),
    UNIQUE KEY uk_txn_idem (idem_key),
    KEY idx_txn_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水（uk(txn_no)、uk(idem_key) 为幂等基石）';
