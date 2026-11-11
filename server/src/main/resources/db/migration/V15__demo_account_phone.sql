-- V15：用户端演示账号登录标识改为大陆手机号
-- 背景：客户账号以「手机号」作为登录标识（LoginUseCase 优先 findByPhone），
--       原 V11 种子的 9002 账号 phone 为 13800009002，此处统一覆盖为真实格式手机号。
-- 密码仍为明文占位 123456（与 UserAccount 明文口径一致）。
UPDATE customer SET phone = '13800138000' WHERE id = 9002;
-- 兜底：历史库若 id 偏移，按客户编号再对齐一次
UPDATE customer SET phone = '13800138000' WHERE customer_no = 'CUS-9002';
