-- V31 下架历史业务参数 chat.session.idle.minutes
--
-- 背景（2026-11-16）：客服会话「客户 10 分钟无消息自动结束」的阈值已改为应用配置
--   app.chat.idle-minutes（application.yml，可用环境变量 CHAT_IDLE_MINUTES 覆盖）。
--   sys_config 里的这条从 V22 灌入起就没有任何代码读取——留在业务参数页只会让运营
--   改了不生效（还可能被误当成真源），因此直接下架。
--
-- 用 DELETE 而不是改名加 .history 后缀：要求「聊天超时配置在全库只有一个真源」，
-- 留一条废弃键仍然会被人翻到、被人编辑，反而增加歧义。
DELETE FROM sys_config WHERE config_key = 'chat.session.idle.minutes';
