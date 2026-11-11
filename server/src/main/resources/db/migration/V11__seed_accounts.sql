-- V11 验收种子数据（开发 / 测试 / E2E）
-- 说明：仓库已有 V1~V10，若用 V1.1 会成为「落后于已应用版本」的迁移而无法执行，故顺延为 V11。
-- 全部 INSERT IGNORE，保证已有数据的开发库重复执行也不会破坏业务数据。

-- ---------- 管理端账号：admin / 123456（userId = 9001） ----------
INSERT IGNORE INTO sys_role (id, code, name, data_scope, is_builtin)
VALUES (1, 'SUPER_ADMIN', '超级管理员', 'ALL', 1);
INSERT IGNORE INTO sys_permission (id, code, name, module, type)
VALUES (1, '*', '全部权限', 'ALL', 'ACTION');
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (1, 1);
INSERT IGNORE INTO sys_staff
    (id, staff_no, name, phone, email, password_hash, role_id, status, token_version, deleted)
VALUES
    (9001, 'ADMIN001', '验收管理员', '13800009001', 'admin@sent.test', '123456', 1, 'ACTIVE', 0, 0);

-- ---------- 用户端账号：customer / 123456（userId = 9002） ----------
INSERT IGNORE INTO customer
    (id, customer_no, phone, password_hash, nickname, type, level_code,
     growth_value, points_balance, status, token_version, deleted)
VALUES
    (9002, 'CUS-9002', '13800009002', '123456', '验收客户', 'PERSON', 'LV1',
     0, 0, 'ACTIVE', 0, 0);

-- ---------- 商品目录 / SKU / 库存（供目录真查询与 E2E 下单） ----------
INSERT IGNORE INTO product_category (id, parent_id, name, sort)
VALUES (1, NULL, '球类运动', 1);
INSERT IGNORE INTO product
    (id, product_no, title, category_id, brand, status, published_at, sales_count, deleted)
VALUES
    (1001, 'P-1001', '森特专业比赛篮球', 1, 'SENT', 'ON_SHELF', NOW(3), 0, 0);
INSERT IGNORE INTO sku
    (id, spu_id, sku_no, retail_price_cent, floor_price_cent, cost_price_cent, status)
VALUES
    (9101, 1001, 'SKU-9101', 10000, 8000, 5000, 'ON'),
    (9102, 1001, 'SKU-9102', 20000, 16000, 10000, 'ON');
INSERT IGNORE INTO sku_stock
    (sku_id, physical_qty, locked_qty, pending_qty, frozen_qty, safety_qty)
VALUES
    (9101, 100, 0, 0, 0, 10),
    (9102, 100, 0, 0, 0, 10);

-- ---------- 购物车演示行（GET /api/cart 真查询可见） ----------
INSERT IGNORE INTO cart_item
    (customer_id, sku_id, qty, customize_flag, selected, price_snapshot_cent)
VALUES
    (9002, 9101, 2, 0, 1, 10000);
