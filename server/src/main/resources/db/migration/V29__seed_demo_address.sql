-- 下单必填收货地址（2026-11-15）：给演示客户补一条默认地址，否则验收 / E2E 一进结算页就无地址可选。
-- 只在「该客户还没有任何地址」时插入，重复执行 / 用户已自建地址都不会被覆盖。
INSERT INTO customer_address (customer_id, receiver, phone, province, city, district, detail, is_default)
SELECT 9002, '验收客户', '13800138000', '江苏省', '南京市', '玄武区', '龙蟠路 100 号 森特大厦 8 楼', 1
WHERE NOT EXISTS (SELECT 1 FROM customer_address WHERE customer_id = 9002);
