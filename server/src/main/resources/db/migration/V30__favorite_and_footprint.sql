-- V30 商品收藏 / 浏览足迹 / 会员专属商品（2026-11-16）
--
-- 背景：商城一直缺「收藏」和「足迹」两件事——个人中心的两张卡只能写死空态，
-- 会员中心的「会员专属商品」也只能返回空列表。本次补齐：
--   ① product_favorite  客户收藏（一人一商品一条，唯一键防重复收藏）
--   ② product_footprint 客户浏览足迹（一人一商品一条，重复浏览只刷新时间、累加次数，
--      否则详情页每刷一次就会在足迹里多出一条一模一样的记录）
--   ③ product.min_visible_level 播入等级门槛，让「会员专属商品」有真实数据可查
--      （该列此前没有任何 SQL 读取，接上不会影响商品列表口径）

CREATE TABLE IF NOT EXISTS product_favorite (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    customer_id BIGINT UNSIGNED NOT NULL                COMMENT '收藏人（customer.id）',
    product_id  BIGINT UNSIGNED NOT NULL                COMMENT '被收藏的 SPU（product.id）',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT          NULL                    COMMENT '操作人，未登录场景留空',
    PRIMARY KEY (id),
    -- 防重复收藏只靠唯一索引（项目口径：禁分布式锁），重复收藏走 INSERT IGNORE 幂等
    UNIQUE KEY uk_favorite_customer_product (customer_id, product_id),
    KEY idx_favorite_customer (customer_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='商品收藏';

CREATE TABLE IF NOT EXISTS product_footprint (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    customer_id    BIGINT UNSIGNED NOT NULL                COMMENT '浏览人（customer.id）',
    product_id     BIGINT UNSIGNED NOT NULL                COMMENT '浏览的 SPU（product.id）',
    view_count     INT             NOT NULL DEFAULT 1      COMMENT '累计浏览次数',
    last_viewed_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近一次浏览时间',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by     BIGINT          NULL,
    PRIMARY KEY (id),
    -- 同一商品只留一条足迹：重复浏览走 ON DUPLICATE KEY UPDATE，只刷新时间与次数
    UNIQUE KEY uk_footprint_customer_product (customer_id, product_id),
    KEY idx_footprint_customer_time (customer_id, last_viewed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='商品浏览足迹';

-- 会员专属商品：LV1 起可见 / LV2 起可见 / LV3 起可见各播几条在售商品。
-- 用 WHERE min_visible_level IS NULL 守卫，演示环境被手工改过也不会覆盖。
UPDATE product SET min_visible_level = 'LV1'
WHERE id IN (9101, 9102, 9107, 9110) AND min_visible_level IS NULL;
UPDATE product SET min_visible_level = 'LV2'
WHERE id IN (9108, 9112, 9118) AND min_visible_level IS NULL;
UPDATE product SET min_visible_level = 'LV3'
WHERE id IN (9105, 9111) AND min_visible_level IS NULL;

-- 演示客户 9002（13800138000）：播几条收藏与足迹，验收时两张卡都有内容。
-- 收藏 / 足迹都只在「该客户还没有任何记录」时插入，重复执行不会覆盖用户的真实数据。
INSERT INTO product_favorite (customer_id, product_id, created_at, updated_at)
SELECT 9002, p.id, NOW(3), NOW(3)
FROM product p
WHERE p.id IN (9101, 9102, 9104)
  AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM product_favorite f WHERE f.customer_id = 9002);

INSERT INTO product_footprint (customer_id, product_id, view_count, last_viewed_at, created_at, updated_at)
SELECT 9002, p.id, 1, NOW(3), NOW(3), NOW(3)
FROM product p
WHERE p.id IN (9107, 9110, 9105)
  AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM product_footprint fp WHERE fp.customer_id = 9002);
