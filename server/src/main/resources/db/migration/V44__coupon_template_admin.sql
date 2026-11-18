-- 优惠券模板管理（2026-11-18 用户需求 2）：
--   管理端要能自己创建「特定商品」的优惠券，创建后能看到券号 / 创建人 / 创建时间，
--   也能删除（软删）与导出；发放记录同样要能删除。
--
-- 删除一律软删：已发出的券是模板快照，删模板不影响历史券与订单抵扣；
-- 删「发放记录」只把这条券从列表与「我的优惠券」里摘掉，不改订单金额。

ALTER TABLE coupon_template
    ADD COLUMN template_no VARCHAR(32) DEFAULT NULL AFTER id,
    ADD COLUMN created_by  BIGINT      DEFAULT NULL AFTER status,
    ADD COLUMN deleted     TINYINT     NOT NULL DEFAULT 0 AFTER created_by;

-- 存量模板补券号与创建人：券号按主键派生，天然唯一，便于运营口头对账
UPDATE coupon_template SET template_no = CONCAT('CPT', LPAD(id, 6, '0')) WHERE template_no IS NULL;
UPDATE coupon_template SET created_by = 9001 WHERE created_by IS NULL;

ALTER TABLE coupon_template
    MODIFY COLUMN template_no VARCHAR(32) NOT NULL,
    ADD UNIQUE KEY uk_template_no (template_no),
    ADD KEY idx_template_deleted (deleted, created_at);

ALTER TABLE customer_coupon
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 AFTER status,
    ADD KEY idx_coupon_deleted (deleted, created_at);
