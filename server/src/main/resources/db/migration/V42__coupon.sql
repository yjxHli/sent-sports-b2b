-- 优惠券模块（2026-11-18 用户需求）：
-- 券模板定义面额档位，用户持有券保存实际门槛（发放时可覆盖模板默认值）。
-- 金额单位一律为分，与全站金额口径一致。

CREATE TABLE coupon_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    amount_cent BIGINT NOT NULL,
    threshold_cent BIGINT NOT NULL DEFAULT 0,
    valid_days INT NOT NULL DEFAULT 30,
    scene VARCHAR(16) NOT NULL DEFAULT 'ADMIN',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_template_scene (scene, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_coupon (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id BIGINT UNSIGNED NOT NULL,
    template_id BIGINT UNSIGNED NOT NULL,
    coupon_no VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    amount_cent BIGINT NOT NULL,
    threshold_cent BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'UNUSED',
    source VARCHAR(16) NOT NULL,
    reason VARCHAR(255) DEFAULT NULL,
    expire_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) DEFAULT NULL,
    order_no VARCHAR(64) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_no (coupon_no),
    KEY idx_coupon_customer (customer_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 注册礼：5 元无门槛 / 满 20 减 10 / 满 80 减 50
INSERT INTO coupon_template (id, name, amount_cent, threshold_cent, valid_days, scene, status) VALUES
    (1, '新用户无门槛券',   500,    0, 30, 'REGISTER', 'ACTIVE'),
    (2, '新用户满20减10',  1000, 2000, 30, 'REGISTER', 'ACTIVE'),
    (3, '新用户满80减50',  5000, 8000, 30, 'REGISTER', 'ACTIVE');

-- 管理端可选的 9 个面额档（门槛是默认值，发放时可自行设定）
INSERT INTO coupon_template (id, name, amount_cent, threshold_cent, valid_days, scene, status) VALUES
    (10, '5元券',   500,    0, 30, 'ADMIN', 'ACTIVE'),
    (11, '8元券',   800, 3000, 30, 'ADMIN', 'ACTIVE'),
    (12, '10元券', 1000, 5000, 30, 'ADMIN', 'ACTIVE'),
    (13, '15元券', 1500, 8000, 30, 'ADMIN', 'ACTIVE'),
    (14, '20元券', 2000,10000, 30, 'ADMIN', 'ACTIVE'),
    (15, '30元券', 3000,15000, 30, 'ADMIN', 'ACTIVE'),
    (16, '50元券', 5000,20000, 30, 'ADMIN', 'ACTIVE'),
    (17, '80元券', 8000,30000, 30, 'ADMIN', 'ACTIVE'),
    (18, '100元券',10000,50000, 30, 'ADMIN', 'ACTIVE');

-- 发券权限：复用「客户授信」权限码，补授给客服经理（role 7）用于售后补券
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 7, id FROM sys_permission WHERE code = 'customer:grant';
