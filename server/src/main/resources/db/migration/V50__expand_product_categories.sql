-- V50 扩充商品分类：首页「全部商品分类」只有 4 项（球类运动 / 足球 / 篮球 / 羽毛球），
-- 而演示库里的商品已经覆盖排球、网球、健身器材、瑜伽、跑步、户外、游泳、服饰、配件等品类，
-- 分类导航既少又与商品实际类型对不上（大量排球商品挤在「球类运动」下）。
--
-- 本迁移只做两件事：① 补齐分类字典；② 按商品标题关键词把已有 SPU 归到对应分类。
-- 全部幂等：分类用 NOT EXISTS 去重，归类用 LIKE 覆盖，重复执行结果一致。
-- 约束：不删表、不删列、不 TRUNCATE（R-01）；不改表结构（表数仍为 63）。

-- ---------- 1. 新增分类（parent_id = 1 表示球类运动下的二级类目） ----------
INSERT INTO product_category (parent_id, name, sort)
SELECT s.parent_id, s.name, s.sort
FROM (
    SELECT 1 AS parent_id, '排球' AS name, 4 AS sort
    UNION ALL SELECT 1, '网球', 5
    UNION ALL SELECT NULL, '健身器材', 6
    UNION ALL SELECT NULL, '瑜伽用品', 7
    UNION ALL SELECT NULL, '跑步装备', 8
    UNION ALL SELECT NULL, '户外运动', 9
    UNION ALL SELECT NULL, '游泳装备', 10
    UNION ALL SELECT NULL, '运动服饰', 11
    UNION ALL SELECT NULL, '运动配件', 12
) s
WHERE NOT EXISTS (SELECT 1 FROM product_category c WHERE c.name = s.name);

-- ---------- 2. 按标题关键词归类（先具体运动，后装备/服饰/配件） ----------
UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '排球' LIMIT 1)
WHERE title LIKE '%排球%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '羽毛球' LIMIT 1)
WHERE title LIKE '%羽毛球%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '网球' LIMIT 1)
WHERE title LIKE '%网球%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '篮球' LIMIT 1)
WHERE title LIKE '%篮球%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '足球' LIMIT 1)
WHERE title LIKE '%足球%';

-- 品牌通用球（斯伯丁 / 耐克 / 威尔胜）留在「球类运动」作为综合球类，
-- 保证该分类下有商品可点（首页「球类专场」瓦片指向它）。
UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '球类运动' LIMIT 1)
WHERE title LIKE '%斯伯丁%' OR title LIKE '%耐克%' OR title LIKE '%威尔胜%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '瑜伽用品' LIMIT 1)
WHERE title LIKE '%瑜伽%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '跑步装备' LIMIT 1)
WHERE title LIKE '%跑步鞋%' OR title LIKE '%跑鞋%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '游泳装备' LIMIT 1)
WHERE title LIKE '%泳镜%' OR title LIKE '%泳衣%' OR title LIKE '%泳帽%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '健身器材' LIMIT 1)
WHERE title LIKE '%哑铃%' OR title LIKE '%壶铃%' OR title LIKE '%弹力带%'
   OR title LIKE '%平衡球%' OR title LIKE '%跳绳%' OR title LIKE '%悬挂训练带%'
   OR title LIKE '%护腕%' OR title LIKE '%护膝%' OR title LIKE '%护腰%'
   OR title LIKE '%手套%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '户外运动' LIMIT 1)
WHERE title LIKE '%登山杖%' OR title LIKE '%滑板%' OR title LIKE '%飞盘%'
   OR title LIKE '%攀岩%' OR title LIKE '%骑行%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '运动服饰' LIMIT 1)
WHERE title LIKE '%T恤%' OR title LIKE '%长裤%';

UPDATE product SET category_id = (SELECT id FROM product_category WHERE name = '运动配件' LIMIT 1)
WHERE title LIKE '%毛巾%' OR title LIKE '%水壶%' OR title LIKE '%双肩包%'
   OR title LIKE '%能量补给%' OR title LIKE '%心率带%';
