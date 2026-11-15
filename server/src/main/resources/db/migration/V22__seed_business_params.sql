-- V22 业务参数默认值（sys_config）
-- 背景：业务参数页此前恒为空（后端 SystemServiceImpl.listParams 是空桩），修好读侧之后
-- 仍需要一套可运营的默认参数，否则页面依旧是空表。这里只灌「系统运行真正会用到的口径」，
-- 全部 INSERT IGNORE：已存在同名键时保留运营改过的值，不会被迁移覆盖。

INSERT IGNORE INTO sys_config (config_key, config_value, remark) VALUES
    ('order.pay.timeout.minutes', '30', '待付款订单超时自动关闭（分钟）'),
    ('order.auto.confirm.days', '7', '发货后自动确认收货（天）'),
    ('order.close.after.days', '3', '已完成订单禁止售后（天）'),
    ('stock.warning.threshold', '10', '库存预警阈值（件）'),
    ('finance.invoice.tax.rate', '13', '发票税率（%）'),
    ('finance.credit.default.limit.cent', '5000000', '企业客户默认授信额度（分）'),
    ('finance.credit.default.term.days', '30', '默认账期（天）'),
    ('member.point.exchange.ratio', '100', '积分兑换比例：100 积分 = 1 元'),
    ('member.level.upgrade.growth', '1000', '每级成长值门槛（点）'),
    ('chat.session.idle.minutes', '15', '客服会话空闲转接待池（分钟）');
