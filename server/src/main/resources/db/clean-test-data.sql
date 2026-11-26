-- ============================================================================
-- 森特体育 · 本地测试数据清理脚本
-- ----------------------------------------------------------------------------
-- 适用库：sent_dev（本地开发） / sent_test
-- 数据库：MySQL 8.0
--
-- 【红线】只用 DELETE / UPDATE。禁止 TRUNCATE、DROP、ALTER。
-- 【保留】sys_staff / sys_role / sys_permission / sys_role_permission / sys_config
--         product_category / product / sku / sku_stock / ladder_price
--         flyway_schema_history
--         员工 9001~9008（不加 WHERE 即不触碰 sys_staff，等于全保留）
--         客户 9002（用户端验收账号）+ 91001~91006（3 企业 + 3 普通）
--
-- 【配置类：本脚本一行都不删】
--         faq_knowledge（智能客服话术，含 hit_count 命中次数 / status 启停 / deleted 软删）
--         chat_quick_reply（客服快捷回复）  sys_sensitive_word（敏感词）
--         customer_tag（客户标签库）        notify_template（通知模板）
--         coupon_template（优惠券模板）     flash_sale_activity 非 E2E 的部分
--         唯一例外：coupon_template.claimed_qty 会被归零（见 9.3），模板行本身不删。
--         注：customer_tag_link 只清被删客户的绑定，标签库本身不动。
--
-- 【为什么保留 91001~91006】
--   91001/91002/91003 由 V14__seed_demo_data.sql 种入（3 个企业客户）；
--   e2e/chat-concurrency.spec.ts:23 把 [91001,91002,91003] 写成常量填充客户，
--   e2e/enterprise-self-register.spec.ts:22 断言 13900009101 可见 —— 删掉这三条会直接打红两条用例。
--   91004~91006 是随附的 3 个普通客户（会员1~3），即需求的「3 个普通用户」。
--   其余 776 条 id >= 91007 的全部删除。
--
-- 【执行方式】见文件末尾。
-- ============================================================================

SET NAMES utf8mb4;
SET SQL_SAFE_UPDATES = 0;

-- ---------------------------------------------------------------------------
-- 0. 预检：先看库名对不对（不是 sent_dev / sent_test 就 Ctrl+C）
-- ---------------------------------------------------------------------------
SELECT DATABASE() AS current_schema, NOW(3) AS run_at;

-- 建议先跑一遍「预演」，确认待删行数量符合预期，再执行正式段：
--   SELECT 'chat_message' t, COUNT(*) c FROM chat_message
--   UNION ALL SELECT 'orders', COUNT(*) FROM orders
--   UNION ALL SELECT 'customer(待删)', COUNT(*) FROM customer WHERE id > 9100 AND id NOT IN (91001,91002,91003,91004,91005,91006)
--   UNION ALL SELECT 'sku_stock', COUNT(*) FROM sku_stock;

START TRANSACTION;

-- ===========================================================================
-- 1. 客服域（子表 → 主表）
-- ===========================================================================
DELETE FROM chat_message;          -- 聊天消息
DELETE FROM chat_read_cursor;      -- 已读游标
DELETE FROM chat_satisfaction;     -- 满意度评价
DELETE FROM chat_session_note;     -- 服务工单（V65 暂存/提交两态都在这里）
DELETE FROM chat_session_tag;      -- 会话-标签
DELETE FROM chat_session;          -- 会话主表
DELETE FROM user_message;          -- 站内信（异步通知，非会话）
DELETE FROM notification;          -- 站内通知

-- ===========================================================================
-- 2. 售后：退款（先子后主）
-- ===========================================================================
DELETE FROM refund_txn;            -- 退款流水（引用 refund_order）
DELETE FROM refund_order;          -- 退款单（引用 orders）

-- ===========================================================================
-- 3. 定制（draft 依赖 custom_order，custom_order 依赖 order_item）
-- ===========================================================================
DELETE FROM custom_change_request; -- 改稿申请
DELETE FROM custom_review_log;     -- 图稿审核日志
DELETE FROM custom_draft;          -- 图稿（引用 custom_order_id）
DELETE FROM custom_order;          -- 定制单（引用 order_item_id）

-- ===========================================================================
-- 4. 交易核心（子表 → 主表）
-- ===========================================================================
DELETE FROM shipment_item;         -- 发货明细
DELETE FROM shipment;              -- 发货单
DELETE FROM payment_txn;           -- 支付流水（按 order_no 关联）
DELETE FROM invoice_request;       -- 开票申请（引用 order_id）
DELETE FROM order_item;            -- 订单明细
DELETE FROM order_status_log;      -- 订单状态流水
DELETE FROM cart_item;             -- 购物车
DELETE FROM orders;                -- 订单主表

-- ===========================================================================
-- 5. 财务（应收/回款/核销/合同，全部挂在订单或客户上）
-- ===========================================================================
DELETE FROM write_off;             -- 核销
DELETE FROM payment_record;        -- 回款
DELETE FROM receivable;            -- 应收账款
DELETE FROM contract;              -- 合同

-- ===========================================================================
-- 6. 营销 / 评价 / 客户行为
-- ===========================================================================
DELETE FROM customer_coupon;       -- 用户已领的券（真实表名是 customer_coupon，不是 user_coupon）；券模板 coupon_template 保留
DELETE FROM product_review;        -- 商品评价
DELETE FROM product_favorite;      -- 收藏
DELETE FROM product_footprint;     -- 足迹
DELETE FROM point_log;             -- 积分流水
DELETE FROM growth_log;            -- 成长值流水
DELETE FROM stock_flow;            -- 库存流水（配合第 7 步库存复位）

-- ===========================================================================
-- 7. 客户附属表（只清待删客户的，保留账号的档案必须留下）
--    保留集 = 9002, 91001, 91002, 91003, 91004, 91005, 91006
-- ===========================================================================
DELETE FROM enterprise_profile
 WHERE customer_id > 9100
   AND customer_id NOT IN (91001, 91002, 91003);   -- 只清被删企业的授信档案
DELETE FROM customer_address
 WHERE customer_id > 9100
   AND customer_id NOT IN (9002, 91001, 91002, 91003, 91004, 91005, 91006);
DELETE FROM customer_owner_log;      -- 归属变更日志（全表可清）
DELETE FROM customer_follow_log;     -- 跟进记录（全表可清）
DELETE FROM arrival_subscription;    -- 到货订阅
DELETE FROM customer_tag_link
 WHERE customer_id > 9100
   AND customer_id NOT IN (9002, 91001, 91002, 91003, 91004, 91005, 91006);
DELETE FROM customer_sku_price
 WHERE customer_id > 9100
   AND customer_id NOT IN (9002, 91001, 91002, 91003, 91004, 91005, 91006);
DELETE FROM agreement_price
 WHERE customer_id > 9100
   AND customer_id NOT IN (9002, 91001, 91002, 91003, 91004, 91005, 91006);

-- ===========================================================================
-- 8. 删客户：只删 DemoDataGenerator 造的那几百个（id >= 91007）
-- ===========================================================================
DELETE FROM customer
 WHERE id > 9100
   AND id NOT IN (91001, 91002, 91003, 91004, 91005, 91006);

-- ===========================================================================
-- 9. 状态复位
-- ===========================================================================
-- 9.1 库存：实物补到 1000，清掉预占/待发/冻结。
--     StockItem.availableQty() = max(0, physical - locked - pending - frozen)，
--     只清 locked 会留下 pending/frozen 的残量（当前库 pending/frozen 恰好为 0，仍建议一起清）。
UPDATE sku_stock
   SET physical_qty = 1000,
       locked_qty   = 0,
       pending_qty  = 0,
       frozen_qty   = 0;
-- 注：version 是乐观锁版本号，故意不动 —— 清库后没有并发事务，递增反而可能和缓存里的旧值错位。

-- 9.2 验收客户积分复位
UPDATE customer SET points_balance = 50000 WHERE id = 9002;

-- 9.3 去规范化计数器复位（这些字段不会随订单删除自动回滚，必须手工归零，否则页面显示假数据）
--     product.sales_count：DemoDataGenerator:497 随机 0~500 写入，业务里从未累加，商品卡「已售 N」会全是假的。
UPDATE product SET sales_count = 0;
--     coupon_template.claimed_qty：删了 customer_coupon 后已领数仍留在模板上，可能把券判成「已领完」。
UPDATE coupon_template SET claimed_qty = 0;
--     保留企业的已用授信归零（应收已清空）
UPDATE enterprise_profile SET credit_used_cent = 0
 WHERE customer_id IN (91001, 91002, 91003);

-- ===========================================================================
-- 10. 清 E2E 脏数据
-- ===========================================================================
-- 10.1 操作日志：前缀 E2E 或含「测试」
DELETE FROM sys_operation_log
 WHERE module    LIKE 'E2E%'
    OR action    LIKE 'E2E%'
    OR target_id LIKE 'E2E%'
    OR reason    LIKE '%测试%';
-- 想连开发期点出来的日志一并清掉，改用下面这句（默认不执行）：
-- DELETE FROM sys_operation_log;

-- 10.2 E2E 遗留的秒杀活动（当前库 48 条，title 形如「E2E限时秒杀-182573」）
DELETE FROM flash_sale_item
 WHERE activity_id IN (SELECT id FROM flash_sale_activity WHERE title LIKE 'E2E%');
DELETE FROM flash_sale_activity WHERE title LIKE 'E2E%';

COMMIT;

-- ===========================================================================
-- 11. 校验（清理后应看到的结果）
-- ===========================================================================
SELECT 'orders' t, COUNT(*) c FROM orders
UNION ALL SELECT 'order_item',          COUNT(*) FROM order_item
UNION ALL SELECT 'payment_txn',         COUNT(*) FROM payment_txn
UNION ALL SELECT 'shipment',            COUNT(*) FROM shipment
UNION ALL SELECT 'refund_order',        COUNT(*) FROM refund_order
UNION ALL SELECT 'custom_order',        COUNT(*) FROM custom_order
UNION ALL SELECT 'custom_draft',        COUNT(*) FROM custom_draft
UNION ALL SELECT 'chat_session',        COUNT(*) FROM chat_session
UNION ALL SELECT 'chat_message',        COUNT(*) FROM chat_message
UNION ALL SELECT 'chat_session_note',   COUNT(*) FROM chat_session_note
UNION ALL SELECT 'customer_coupon',     COUNT(*) FROM customer_coupon
UNION ALL SELECT 'product_review',      COUNT(*) FROM product_review
UNION ALL SELECT 'stock_flow',          COUNT(*) FROM stock_flow
UNION ALL SELECT 'point_log',           COUNT(*) FROM point_log
UNION ALL SELECT 'growth_log',          COUNT(*) FROM growth_log
UNION ALL SELECT 'write_off',           COUNT(*) FROM write_off;

-- 11.1 保留账号清单（应为 7 行：9002 + 91001~91006）
SELECT id, customer_no, phone, type,
       CASE WHEN id = 9002 THEN 'USER' ELSE 'DEMO' END AS role_note,
       points_balance
  FROM customer
 ORDER BY id;

-- 11.2 权限/基础数据未被破坏（员工应为 7，商品与 SKU 数量不变）
SELECT 'sys_staff' t, COUNT(*) c FROM sys_staff
UNION ALL SELECT 'sys_role',            COUNT(*) FROM sys_role
UNION ALL SELECT 'sys_permission',      COUNT(*) FROM sys_permission
UNION ALL SELECT 'sys_role_permission', COUNT(*) FROM sys_role_permission
UNION ALL SELECT 'sys_config',          COUNT(*) FROM sys_config
UNION ALL SELECT 'product_category',    COUNT(*) FROM product_category
UNION ALL SELECT 'product',             COUNT(*) FROM product
UNION ALL SELECT 'sku',                 COUNT(*) FROM sku
UNION ALL SELECT 'sku_stock',           COUNT(*) FROM sku_stock
UNION ALL SELECT 'ladder_price',        COUNT(*) FROM ladder_price
UNION ALL SELECT 'flyway_schema_history', COUNT(*) FROM flyway_schema_history;

-- 11.3 库存复位结果（available 应全部 1000）
SELECT COUNT(*) AS sku_cnt,
       MIN(physical_qty) AS min_physical,
       MAX(locked_qty)   AS max_locked,
       MIN(physical_qty - locked_qty - pending_qty - frozen_qty) AS min_available
  FROM sku_stock;

-- 11.4 孤儿检查（三项都应为 0；建表时没建外键，只能自己查）
SELECT
  (SELECT COUNT(*) FROM order_item oi LEFT JOIN orders o ON o.id = oi.order_id WHERE o.id IS NULL) AS orphan_order_item,
  (SELECT COUNT(*) FROM refund_order r LEFT JOIN orders o ON o.id = r.order_id WHERE o.id IS NULL)  AS orphan_refund,
  (SELECT COUNT(*) FROM chat_message m LEFT JOIN chat_session s ON s.id = m.session_id WHERE s.id IS NULL) AS orphan_chat_msg;

-- ============================================================================
-- 【执行方式】
--   cd "E:/Sent Sports Goods Management System"
--   "/d/MySQL/MySQL Server 8.0/bin/mysql" -uroot -p123456 \
--       --default-character-set=utf8mb4 \
--       --default-auth=mysql_native_password \
--       sent_dev < server/src/main/resources/db/clean-test-data.sql
--
--   只看预演不执行：把 START TRANSACTION 到 COMMIT 之间整段注释掉，只保留第 0 节和校验段。
--   出错回滚：脚本是单事务的，任何一句报错都会让 mysql 中断 → 连接断开自动 ROLLBACK，库不会有半清理状态。
--
-- 【执行后注意】
--   1) 不要直接重启后端：dev 的 app.demo-data.enabled=true，DemoDataGenerator 见
--      订单 <=100 且客户 <50，会立刻重新灌 200 客户 + 2000 订单 + 会话（run() 的幂等闸门就是 >100 才跳过）。
--      必须用 --app.demo-data.enabled=false 启动。
--   2) 重启不是必须的：MyBatis-Plus 无二级缓存，代码里也没有 RedisTemplate 使用（Redis 仅配置存在、健康检查已关），
--      每次请求都直接读库。但进程内还有 InMemory 的 refresh token / 登出黑名单 / 短信码 / 客服在线状态，
--      清库后旧 token 指向已删除客户，前端刷新会拿不到用户 —— 让三端重新登录即可，或按 1) 的方式安全重启。
-- ============================================================================
