-- =====================================================================================
-- V69：删除 14 张「建了但从未接入业务」的废表（2026-11-28 冗余审计 P1）
--
-- 判据（见 docs/audit-2026-11-28-redundancy.md §3）：表名在 server/src/main/java 中零引用、
-- 无 @TableName 实体、无 Mapper、表内 0 行，且业务能力已由别的表承担。
--
-- 逐张说明「为什么可以删」：
--   arrival_subscription   到货订阅功能未实现
--   chat_quick_reply       话术已改由 faq_knowledge 承担（V59/V60）
--   chat_read_cursor       已读位点走 WebSocket 帧，未落库
--   chat_session_tag       会话标签未实现；客户标签另用 customer_tag
--   custom_change_request  定制「变更申请」未实现
--   customer_follow_log    客户跟进记录未实现
--   file_object            文件走 LocalFileStorage 直接落盘，无元数据表
--   member_level           等级/积分/成长值直接落在 customer（MemberTier 为唯一真源）
--   notification           站内信统一走 user_message
--   notify_template        同上，模板未使用
--   refund_txn             退款走 refund_order + payment_txn
--   stock_inbound          手工入库写 stock_flow（INBOUND）
--   stock_loss             报损写 stock_flow（LOSS）
--   order_status_log       OrderMapper.insertStatusLog 有语句但零调用
--
-- 保留（本轮已接回真库，见 P0）：stock_check / stock_check_item / customer_owner_log。
--
-- 为什么开 FOREIGN_KEY_CHECKS：项目约定表间无物理外键（见 plan §6.2），但历史环境
-- 可能残留手工加的外键，关掉开关再删可避免因依赖顺序失败；删完立刻恢复，不影响后续语句。
-- 注意：DROP TABLE IF EXISTS 可重复执行，回滚时按 Flyway 约定另出 U 脚本（本项目未启用 undo）。
-- =====================================================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `arrival_subscription`;
DROP TABLE IF EXISTS `chat_quick_reply`;
DROP TABLE IF EXISTS `chat_read_cursor`;
DROP TABLE IF EXISTS `chat_session_tag`;
DROP TABLE IF EXISTS `custom_change_request`;
DROP TABLE IF EXISTS `customer_follow_log`;
DROP TABLE IF EXISTS `file_object`;
DROP TABLE IF EXISTS `member_level`;
DROP TABLE IF EXISTS `notification`;
DROP TABLE IF EXISTS `notify_template`;
DROP TABLE IF EXISTS `refund_txn`;
DROP TABLE IF EXISTS `stock_inbound`;
DROP TABLE IF EXISTS `stock_loss`;
DROP TABLE IF EXISTS `order_status_log`;

SET FOREIGN_KEY_CHECKS = 1;
