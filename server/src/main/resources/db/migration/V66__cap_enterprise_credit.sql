-- V66：企业可授信金额统一下调，最高只能申请 2000 元（200000 分）。
-- 单位：enterprise_profile.credit_limit_cent 为「分」，sys_config 的同名参数也为「分」。
-- 只压总额，不动已用额度与账期；已是 2000 元以下的行保持原值。
-- 与 CreditLine.MAX_TOTAL_CENT / 管理端授信输入上限保持同一口径。

UPDATE enterprise_profile
SET credit_limit_cent = 200000
WHERE credit_limit_cent > 200000;

UPDATE sys_config
SET config_value = '200000',
    remark = '企业客户最高可申请授信额度（分）：200000 分 = 2000.00 元'
WHERE config_key = 'finance.credit.default.limit.cent';
