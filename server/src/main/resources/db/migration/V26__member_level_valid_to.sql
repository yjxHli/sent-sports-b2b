-- 会员等级有效期 + 存量等级回填（缺陷修复）。
-- 1) 人工授级此前只写 customer.level_code，「有效期至」仅落在 sys_operation_log.after_json 里，
--    读面拿不到到期时间，会员中心只能退化成 1970-01-01 或「长期有效」；
-- 2) 成长值经 growth_log 累加后从未回写 level_code，导致「成长值 16167 却仍显示 LV1」，
--    进度条按 LV1 的下一档 5000 顶满、差额算出 -11167。
ALTER TABLE customer
    ADD COLUMN level_valid_to DATETIME(3) NULL COMMENT '人工授级有效期（NULL = 长期有效）' AFTER level_code;

-- 存量回填：只升不降（人工授予的高等级不会被成长值压低）
UPDATE customer
SET level_code = 'LV3'
WHERE deleted = 0
  AND growth_value >= 20000
  AND COALESCE(level_code, 'NORMAL') <> 'LV3';

UPDATE customer
SET level_code = 'LV2'
WHERE deleted = 0
  AND growth_value >= 5000
  AND growth_value < 20000
  AND COALESCE(level_code, 'NORMAL') IN ('NORMAL', 'LV1');

UPDATE customer
SET level_code = 'LV1'
WHERE deleted = 0
  AND growth_value >= 1000
  AND growth_value < 5000
  AND COALESCE(level_code, 'NORMAL') = 'NORMAL';
