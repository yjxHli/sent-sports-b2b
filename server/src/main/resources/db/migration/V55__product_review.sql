-- V55 商品评价（图文）
--
-- 背景：商城详情页要展示「用户评价」，且只允许买过的人评价。此前库里没有任何评价载体，
-- 也没有「谁买过什么」的可读面——本迁移只新增一张表，不改动既有表结构。
--
-- 口径：
--   * 资格由订单侧校验（orders.status='COMPLETED' + order_item 含该 SKU），本表只存结果；
--   * 联合唯一索引 uk_review_order_product 是「同一订单同一商品只能评一次」的唯一防线，
--     业务层不引入锁，重复提交由 DuplicateKeyException 暴露后转成 40901；
--   * images_json 存文件服务返回的 fileId 数组（内容寻址相对路径），二进制不进库；
--   * is_anonymous=1 时对外只显示「匿名用户」，真实昵称仅留在 customer 表。
--
-- 注：created_at/updated_at/created_by 是 BasePO 的自动填充列，缺任何一列 INSERT 都会失败。

CREATE TABLE product_review (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id   BIGINT UNSIGNED NOT NULL COMMENT 'SPU 主键',
    sku_id       BIGINT UNSIGNED NULL COMMENT '被评价的 SKU（下单规格）',
    order_no     VARCHAR(32)     NOT NULL COMMENT '资格来源订单号',
    customer_id  BIGINT UNSIGNED NOT NULL COMMENT '评价人',
    rating       TINYINT         NOT NULL COMMENT '1~5 星',
    content      VARCHAR(500)    NOT NULL COMMENT '评价正文',
    images_json  JSON            NULL COMMENT '评价图 fileId 数组（最多 3 张）',
    is_anonymous TINYINT         NOT NULL DEFAULT 0 COMMENT '1=匿名展示',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_order_product (order_no, product_id),
    KEY idx_review_product (product_id, id),
    KEY idx_review_customer (customer_id),
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价';
