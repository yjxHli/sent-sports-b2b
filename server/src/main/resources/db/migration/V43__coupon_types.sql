-- 优惠券四类券型（2026-11-18 用户需求）：
--   NO_THRESHOLD   无门槛券：促活，5 元无门槛
--   FULL_REDUCTION 满减券：满 20 减 10、满 80 减 50（单笔限用 1 张，由下单接口单券字段保证）
--   CATEGORY       品类券：只对指定 SPU/SKU 的行金额生效，支持按折扣率（9 折）
--   ENTERPRISE     企业专属券：仅企业客户可领可用，大额阶梯满减
--
-- 模板保存规则；用户券在发放/领取时把规则快照下来，后续改模板不影响已发出的券。

ALTER TABLE coupon_template
    ADD COLUMN coupon_type   VARCHAR(16) NOT NULL DEFAULT 'FULL_REDUCTION' AFTER threshold_cent,
    ADD COLUMN discount_type VARCHAR(16) NOT NULL DEFAULT 'AMOUNT' AFTER coupon_type,
    ADD COLUMN discount_rate INT         NOT NULL DEFAULT 100 AFTER discount_type,
    ADD COLUMN scope_type    VARCHAR(16) NOT NULL DEFAULT 'ALL' AFTER discount_rate,
    ADD COLUMN scope_ids     VARCHAR(1024) DEFAULT NULL AFTER scope_type,
    ADD COLUMN customer_scope VARCHAR(16) NOT NULL DEFAULT 'ALL' AFTER scope_ids,
    ADD COLUMN claimable     TINYINT     NOT NULL DEFAULT 0 AFTER customer_scope,
    ADD COLUMN claim_limit   INT         NOT NULL DEFAULT 1 AFTER claimable,
    ADD COLUMN total_qty     INT         NOT NULL DEFAULT -1 AFTER claim_limit,
    ADD COLUMN claimed_qty   INT         NOT NULL DEFAULT 0 AFTER total_qty;

-- 存量券型按门槛反推：0 门槛就是无门槛券，其余是满减券
UPDATE coupon_template SET coupon_type = 'NO_THRESHOLD' WHERE threshold_cent = 0;

ALTER TABLE customer_coupon
    ADD COLUMN coupon_type    VARCHAR(16) NOT NULL DEFAULT 'FULL_REDUCTION' AFTER threshold_cent,
    ADD COLUMN discount_type  VARCHAR(16) NOT NULL DEFAULT 'AMOUNT' AFTER coupon_type,
    ADD COLUMN discount_rate  INT         NOT NULL DEFAULT 100 AFTER discount_type,
    ADD COLUMN scope_type     VARCHAR(16) NOT NULL DEFAULT 'ALL' AFTER discount_rate,
    ADD COLUMN scope_ids      VARCHAR(1024) DEFAULT NULL AFTER scope_type,
    ADD COLUMN customer_scope VARCHAR(16) NOT NULL DEFAULT 'ALL' AFTER scope_ids,
    ADD COLUMN claim_key      VARCHAR(64) DEFAULT NULL AFTER customer_scope,
    -- 防重复领取：同一客户对同一模板只能有一张（claim_key = CLAIM-{templateId}）。
    -- 必须带 customer_id，否则第二个客户领同一张券就会被唯一索引挡掉。
    ADD UNIQUE KEY uk_coupon_claim (customer_id, claim_key);

UPDATE customer_coupon SET coupon_type = 'NO_THRESHOLD' WHERE threshold_cent = 0;

-- 领券中心可领券：无门槛 / 满减 / 品类（SPU、SKU 各一张）/ 企业专属阶梯
INSERT IGNORE INTO coupon_template
    (id, name, amount_cent, threshold_cent, coupon_type, discount_type, discount_rate,
     scope_type, scope_ids, customer_scope, claimable, claim_limit, total_qty, claimed_qty,
     valid_days, scene, status)
VALUES
    (20, '5元无门槛券',        500,      0, 'NO_THRESHOLD',   'AMOUNT',  100, 'ALL', NULL,     'ALL',        1, 1, 10000, 0, 30, 'ADMIN', 'ACTIVE'),
    (21, '满20减10券',        1000,   2000, 'FULL_REDUCTION', 'AMOUNT',  100, 'ALL', NULL,     'ALL',        1, 1, 10000, 0, 30, 'ADMIN', 'ACTIVE'),
    (22, '满80减50券',        5000,   8000, 'FULL_REDUCTION', 'AMOUNT',  100, 'ALL', NULL,     'ALL',        1, 1, 10000, 0, 30, 'ADMIN', 'ACTIVE'),
    (23, '斯伯丁篮球专享9折',     0,      0, 'CATEGORY',       'PERCENT',  90, 'SPU', '9101',   'ALL',        1, 1, 5000,  0, 30, 'ADMIN', 'ACTIVE'),
    (24, '斯伯丁5号球专享8.8折',   0,      0, 'CATEGORY',       'PERCENT',  88, 'SKU', '9201,9202', 'ALL',     1, 1, 5000,  0, 30, 'ADMIN', 'ACTIVE'),
    (25, '企业专享满1000减100', 10000, 100000, 'ENTERPRISE',     'AMOUNT',  100, 'ALL', NULL,     'ENTERPRISE', 1, 1, 2000,  0, 60, 'ADMIN', 'ACTIVE'),
    (26, '企业专享满3000减300', 30000, 300000, 'ENTERPRISE',     'AMOUNT',  100, 'ALL', NULL,     'ENTERPRISE', 1, 1, 1000,  0, 60, 'ADMIN', 'ACTIVE'),
    (27, '企业专享满5000减500', 50000, 500000, 'ENTERPRISE',     'AMOUNT',  100, 'ALL', NULL,     'ENTERPRISE', 1, 1, 500,   0, 60, 'ADMIN', 'ACTIVE');
