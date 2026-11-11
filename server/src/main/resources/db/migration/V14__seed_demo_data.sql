-- V14 演示种子数据（答辩演示专用）：员工 / 企业客户 / 商品 / 订单。
-- 说明：V1~V13 已落地表结构、验收账号与定制账本，本脚本只「灌数据」，不新增任何表（表数仍为 54）。
-- 全部 INSERT IGNORE，保证开发 / 测试库重复执行不会破坏既有业务数据；演示数据 ID 段集中取 9xxxx/91xxx，
-- 与 V11 的 9001~9102、IT 造数（1/88/5001）错开，避免主键冲突。
-- 注意：本脚本落在真实库（含 sent_test），但集成测试基类会在每个用例前 TRUNCATE 全部业务表，故不会污染 IT 断言。

-- ============================ 1. 角色（销售主管 / 销售员 / 财务 / 仓管） ============================
-- 超管(1,SUPER_ADMIN)、客服(2,CUSTOMER_SERVICE) 已在 V11/V13 落地。
-- 演示口径：四个业务角色统一授予 '*'（与超管同权），保证答辩时任意员工登录都能完整走通管理端；
-- 生产环境应按业务域（product/stock/order/finance/...）回收细粒度权限点。
INSERT IGNORE INTO sys_role (id, code, name, data_scope, is_builtin)
VALUES
    (3, 'SALES_MANAGER', '销售主管', 'ALL', 0),
    (4, 'SALES',         '销售员',   'ALL', 0),
    (5, 'FINANCE',       '财务',     'ALL', 0),
    (6, 'WAREHOUSE',     '仓管',     'ALL', 0);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
VALUES
    (3, 1), (4, 1), (5, 1), (6, 1);

-- ============================ 2. 员工（6 人，密码统一 123456，沿用项目明文占位口径） ============================
-- admin(9001)、service(9003) 已在 V11/V13 落地；此处补齐 4 个业务岗，userId 与 AccountSeeder 内存账号严格对齐。
INSERT IGNORE INTO sys_staff
    (id, staff_no, name, phone, email, password_hash, role_id, leader_id, status, fail_count, token_version, deleted)
VALUES
    (9004, 'SALESMGR001', '销售主管·王磊', '13800009004', 'salesmgr@sent.test', '123456', 3, 9001, 'ACTIVE', 0, 0, 0),
    (9005, 'SALES001',    '销售员·李娜',   '13800009005', 'sales@sent.test',    '123456', 4, 9004, 'ACTIVE', 0, 0, 0),
    (9006, 'FIN001',      '财务·赵敏',     '13800009006', 'finance@sent.test',  '123456', 5, 9001, 'ACTIVE', 0, 0, 0),
    (9007, 'WH001',       '仓管·孙强',     '13800009007', 'warehouse@sent.test','123456', 6, 9001, 'ACTIVE', 0, 0, 0);

-- ============================ 3. 商品分类（在 V11「球类运动」下挂二级类目） ============================
INSERT IGNORE INTO product_category (id, parent_id, name, sort)
VALUES
    (2, 1, '足球',   1),
    (3, 1, '篮球',   2),
    (4, 1, '羽毛球', 3);

-- ============================ 4. 商品 SPU（4 个，均上架） ============================
-- 图片用 picsum 稳定图源（seed 固定即同一张图），保证演示环境图片必加载；生产替换为品牌 CDN 真实图。
INSERT IGNORE INTO product
    (id, product_no, title, category_id, brand, main_image, images_json, can_customize, status, published_at, sales_count, deleted)
VALUES
    (9101, 'P-9101', '斯伯丁 5号 比赛用球', 2, 'Spalding',
        'https://picsum.photos/seed/spalding-s5/800/800',
        '["https://picsum.photos/seed/spalding-s5-a/800/800","https://picsum.photos/seed/spalding-s5-b/800/800"]',
        0, 'ON_SHELF', NOW(3), 128, 0),
    (9102, 'P-9102', '耐克 4号 训练球', 2, 'Nike',
        'https://picsum.photos/seed/nike-s4/800/800',
        '["https://picsum.photos/seed/nike-s4-a/800/800","https://picsum.photos/seed/nike-s4-b/800/800"]',
        0, 'ON_SHELF', NOW(3), 356, 0),
    (9103, 'P-9103', '威尔胜 7号 室内外通用球', 3, 'Wilson',
        'https://picsum.photos/seed/wilson-s7/800/800',
        '["https://picsum.photos/seed/wilson-s7-a/800/800","https://picsum.photos/seed/wilson-s7-b/800/800"]',
        1, 'ON_SHELF', NOW(3), 542, 0),
    (9104, 'P-9104', '尤尼克斯 AS-05 比赛级羽毛球', 4, 'YONEX',
        'https://picsum.photos/seed/yonex-as05/800/800',
        '["https://picsum.photos/seed/yonex-as05-a/800/800","https://picsum.photos/seed/yonex-as05-b/800/800"]',
        0, 'ON_SHELF', NOW(3), 879, 0);

-- ============================ 5. SKU（每个 SPU 2~3 个，含零售价/底价/成本） ============================
-- 金额单位：分。零售=retail_price_cent，底价(批发线)=floor_price_cent，成本=cost_price_cent。
INSERT IGNORE INTO sku
    (id, spu_id, sku_no, barcode, spec_json, retail_price_cent, floor_price_cent, cost_price_cent, status)
VALUES
    (9201, 9101, 'SKU-9201', '6921009101', '{"color":"经典黑金"}',   39900, 32000, 25000, 'ON'),
    (9202, 9101, 'SKU-9202', '6921009202', '{"color":"礼盒装"}',     45900, 38000, 30000, 'ON'),
    (9203, 9102, 'SKU-9203', '6921009203', '{"color":"黄蓝配色"}',   19900, 15000, 10000, 'ON'),
    (9204, 9102, 'SKU-9204', '6921009204', '{"color":"红黑配色"}',   20900, 16000, 10500, 'ON'),
    (9205, 9103, 'SKU-9205', '6921009205', '{"color":"室内外通用"}', 29900, 24000, 18000, 'ON'),
    (9206, 9103, 'SKU-9206', '6921009206', '{"color":"夜光版"}',     35900, 29000, 22000, 'ON'),
    (9207, 9103, 'SKU-9207', '6921009207', '{"color":"球队定制版"}', 41900, 34000, 26000, 'ON'),
    (9208, 9104, 'SKU-9208', '6921009208', '{"spec":"一筒(12只)"}',   7500,  6000,  4500, 'ON'),
    (9209, 9104, 'SKU-9209', '6921009209', '{"spec":"一筒(12只)"}',   5500,  4500,  3200, 'ON');

-- ============================ 6. 阶梯价（每个 SKU 两档：零售 / 10 件起批量价） ============================
INSERT IGNORE INTO ladder_price (sku_id, min_qty, max_qty, price_cent)
VALUES
    (9201, 1, 9,   39900), (9201, 10, NULL, 37900),
    (9202, 1, 9,   45900), (9202, 10, NULL, 43900),
    (9203, 1, 9,   19900), (9203, 10, NULL, 18900),  -- 与订单 SO20260101002 的 10 件批量价一致
    (9204, 1, 9,   20900), (9204, 10, NULL, 19900),
    (9205, 1, 9,   29900), (9205, 10, NULL, 27900),
    (9206, 1, 9,   35900), (9206, 10, NULL, 33900),
    (9207, 1, 9,   41900), (9207, 10, NULL, 39900),
    (9208, 1, 9,    7500), (9208, 10, NULL,  7000),
    (9209, 1, 9,    5500), (9209, 10, NULL,  5200);

-- ============================ 7. 库存（演示用统一给 200 件，安全库存 20） ============================
INSERT IGNORE INTO sku_stock
    (sku_id, physical_qty, locked_qty, pending_qty, frozen_qty, safety_qty)
VALUES
    (9201, 200, 0, 0, 0, 20), (9202, 200, 0, 0, 0, 20),
    (9203, 200, 0, 0, 0, 20), (9204, 200, 0, 0, 0, 20),
    (9205, 200, 0, 0, 0, 20), (9206, 200, 0, 0, 0, 20),
    (9207, 200, 0, 0, 0, 20), (9208, 600, 0, 0, 0, 50),
    (9209, 600, 0, 0, 0, 50);

-- ============================ 8. 企业客户（3 个，其中 1 个授信 10 万 + 30 天账期） ============================
INSERT IGNORE INTO customer
    (id, customer_no, phone, password_hash, nickname, email, type, level_code, growth_value, points_balance, owner_staff_id, status, token_version, deleted)
VALUES
    (91001, 'ENT-91001', '13900009101', '123456', '南京市第一中学体育部', 'njyz@school.edu.cn', 'ENTERPRISE', 'LV3', 5000, 2000, 9005, 'ACTIVE', 0, 0),
    (91002, 'ENT-91002', '13900009102', '123456', '跃动青少年体育俱乐部', 'yue@sportsclub.cn',   'ENTERPRISE', 'LV2', 2000,  800, 9005, 'ACTIVE', 0, 0),
    (91003, 'ENT-91003', '13900009103', '123456', '金陵中学体育组',       'jlzx@school.edu.cn',  'ENTERPRISE', 'LV2', 1500,  500, 9005, 'ACTIVE', 0, 0);

-- 企业档案：授信额度（分）、账期（天）、开票信息。
INSERT IGNORE INTO enterprise_profile
    (customer_id, company_name, license_no, license_url, invoice_title, tax_no,
     credit_limit_cent, credit_used_cent, payment_term_days, credit_expire_at, status)
VALUES
    (91001, '南京市第一中学体育部', '91320100XXXXXXXX1A', NULL, '南京市第一中学', '91320100XXXXXXXX1A',
        10000000, 0, 30, NOW(3) + INTERVAL 1 YEAR, 'ACTIVE'),
    (91002, '跃动青少年体育俱乐部', '91320100XXXXXXXX2B', NULL, '跃动青少年体育俱乐部', '91320100XXXXXXXX2B',
         5000000, 0, 15, NOW(3) + INTERVAL 1 YEAR, 'ACTIVE'),
    (91003, '金陵中学体育组',       '91320100XXXXXXXX3C', NULL, '金陵中学', '91320100XXXXXXXX3C',
               0, 0,  0, NULL, 'ACTIVE');

-- ============================ 9. 订单（3 笔不同状态） ============================
-- 9.1 待付款（WAIT_PAY / UNPAID）：南京市第一中学体育部，未支付、含 30 分钟支付倒计时。
INSERT IGNORE INTO orders
    (id, order_no, customer_id, customer_type, owner_staff_id, status, pay_status, custom_status,
     goods_amount_cent, custom_fee_cent, discount_cent, freight_cent, point_deduct_cent,
     payable_cent, paid_cent, received_cent, invoice_flag, remark, expire_at,
     version, idem_key)
VALUES
    (92001, 'SO20260101001', 91001, 'ENTERPRISE', 9005, 'WAIT_PAY', 'UNPAID', NULL,
     109700, 0, 0, 0, 0, 109700, 0, 0, 0, '演示：待付款订单',
     NOW(3) + INTERVAL 30 MINUTE, 0, 'idem-order-92001');

INSERT IGNORE INTO order_item
    (id, order_id, sku_id, spu_title_snap, spec_snap, qty, unit_price_cent, price_source, amount_cent, shipped_qty, custom_flag, custom_fee_cent)
VALUES
    (93001, 92001, 9201, '斯伯丁 5号 比赛用球', '经典黑金', 2, 39900, 'RETAIL', 79800, 0, 0, 0),
    (93002, 92001, 9205, '威尔胜 7号 室内外通用球', '室内外通用', 1, 29900, 'RETAIL', 29900, 0, 0, 0);

-- 9.2 已支付待发货（PAID / WAIT_SHIP）：跃动俱乐部，含 10 件批量价 + 1 件 UV 定制行。
INSERT IGNORE INTO orders
    (id, order_no, customer_id, customer_type, owner_staff_id, status, pay_status, custom_status,
     goods_amount_cent, custom_fee_cent, discount_cent, freight_cent, point_deduct_cent,
     payable_cent, paid_cent, received_cent, invoice_flag, remark, paid_at,
     version, idem_key)
VALUES
    (92002, 'SO20260101002', 91002, 'ENTERPRISE', 9004, 'WAIT_SHIP', 'PAID', 'WAIT_DRAFT',
     224900, 5000, 0, 0, 0, 229900, 229900, 0, 0, '演示：已支付待发货（含定制）',
     NOW(3), 0, 'idem-order-92002');

INSERT IGNORE INTO order_item
    (id, order_id, sku_id, spu_title_snap, spec_snap, qty, unit_price_cent, price_source, amount_cent, shipped_qty, custom_flag, custom_fee_cent)
VALUES
    (93003, 92002, 9203, '耐克 4号 训练球', '黄蓝配色', 10, 18900, 'LADDER', 189000, 0, 0, 0),
    (93004, 92002, 9206, '威尔胜 7号 室内外通用球', '夜光版', 1, 35900, 'RETAIL', 35900, 0, 1, 5000);

-- 支付流水（幂等键兜底重复回调）
INSERT IGNORE INTO payment_txn
    (txn_no, order_no, amount_cent, channel, channel_trade_no, idem_key, status, request_at, callback_at, raw_payload)
VALUES
    ('TXN20260101002', 'SO20260101002', 229900, 'MOCK', 'CH-20260101002', 'idem-pay-92002', 'PAID', NOW(3), NOW(3), '{"ok":true,"channel":"MOCK"}');

-- 定制单：与订单行 93004 一对一绑定（uv 定制，待打样）
INSERT IGNORE INTO custom_order
    (custom_no, order_item_id, customer_id, service_type, print_side, qty, fee_cent, status,
     current_version, reject_count, review_deadline)
VALUES
    ('CO20260101001', 93004, 91002, 'ATTACHED', 'SINGLE', 1, 5000, 'WAIT_DRAFT', 0, 0, NOW(3) + INTERVAL 1 DAY);

-- 9.3 已发货待收货（PAID / SHIPPED）：金陵中学体育组，含包裹与物流单。
INSERT IGNORE INTO orders
    (id, order_no, customer_id, customer_type, owner_staff_id, status, pay_status, custom_status,
     goods_amount_cent, custom_fee_cent, discount_cent, freight_cent, point_deduct_cent,
     payable_cent, paid_cent, received_cent, address_snapshot_json, invoice_flag, remark, paid_at,
     version, idem_key)
VALUES
    (92003, 'SO20260101003', 91003, 'ENTERPRISE', 9005, 'SHIPPED', 'PAID', NULL,
     129300, 0, 0, 0, 0, 129300, 129300, 0,
     '{"receiver":"陈老师","phone":"13900009103","province":"江苏省","city":"南京市","district":"玄武区","detail":"金陵中学体育组器材室"}',
     0, '演示：已发货待收货', NOW(3), 0, 'idem-order-92003');

INSERT IGNORE INTO order_item
    (id, order_id, sku_id, spu_title_snap, spec_snap, qty, unit_price_cent, price_source, amount_cent, shipped_qty, custom_flag, custom_fee_cent)
VALUES
    (93005, 92003, 9208, '尤尼克斯 AS-05 比赛级羽毛球', '一筒(12只)', 5, 7500, 'RETAIL', 37500, 5, 0, 0),
    (93006, 92003, 9202, '斯伯丁 5号 比赛用球', '礼盒装', 2, 45900, 'RETAIL', 91800, 2, 0, 0);

INSERT IGNORE INTO payment_txn
    (txn_no, order_no, amount_cent, channel, channel_trade_no, idem_key, status, request_at, callback_at, raw_payload)
VALUES
    ('TXN20260101003', 'SO20260101003', 129300, 'MOCK', 'CH-20260101003', 'idem-pay-92003', 'PAID', NOW(3), NOW(3), '{"ok":true,"channel":"MOCK"}');

-- 包裹 + 包裹明细（已发 7 件）
INSERT IGNORE INTO shipment
    (shipment_no, order_id, carrier, tracking_no, shipped_at, status, operator_id)
VALUES
    ('SH20260101003', 92003, '顺丰速运', 'SF1234567890123', NOW(3), 'SHIPPED', 9007);

INSERT IGNORE INTO shipment_item
    (shipment_id, order_item_id, qty)
VALUES
    ((SELECT id FROM shipment WHERE shipment_no = 'SH20260101003'), 93005, 5),
    ((SELECT id FROM shipment WHERE shipment_no = 'SH20260101003'), 93006, 2);
