-- 站内信（消息中心）：与在线客服（chat_session / chat_message）是两套东西。
-- 客服是实时会话；站内信是异步通知（订单状态、优惠券、系统公告），用户可稍后再看。
CREATE TABLE IF NOT EXISTS user_message (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    customer_id BIGINT UNSIGNED NOT NULL                COMMENT '接收客户 ID',
    msg_type    VARCHAR(16)     NOT NULL                COMMENT 'ORDER 订单 / PROMO 优惠 / SYSTEM 系统',
    title       VARCHAR(128)    NOT NULL                COMMENT '标题',
    content     VARCHAR(512)    NOT NULL                COMMENT '正文',
    biz_type    VARCHAR(16)     DEFAULT NULL            COMMENT ' ORDER / CUSTOM / COUPON，用于跳转',
    biz_no      VARCHAR(64)     DEFAULT NULL            COMMENT '业务单号（订单号等）',
    read_flag   TINYINT         NOT NULL DEFAULT 0      COMMENT '0 未读 / 1 已读',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_msg_customer (customer_id, read_flag, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='站内信';

-- 演示账号 9002（13800138000）的站内信示例：订单动态 / 优惠 / 系统公告各若干条
INSERT INTO user_message (customer_id, msg_type, title, content, biz_type, biz_no, read_flag, created_at)
VALUES
(9002, 'ORDER',  '订单已发货', '您的订单已由顺丰速运发出，运单号 SF7788990011，预计 2 天内送达。', 'ORDER', NULL, 0, DATE_SUB(NOW(3), INTERVAL 2 HOUR)),
(9002, 'ORDER',  '订单待付款提醒', '您有一笔订单尚未支付，30 分钟后将自动关闭，请及时付款。', 'ORDER', NULL, 0, DATE_SUB(NOW(3), INTERVAL 6 HOUR)),
(9002, 'ORDER',  '定制图稿审核通过', '您的定制队服图稿已通过审核，正在排产，预计 7 天发货。', 'CUSTOM', NULL, 0, DATE_SUB(NOW(3), INTERVAL 1 DAY)),
(9002, 'PROMO',  '双 11 优惠券已到账', '满 500 减 50、满 2000 减 300 各一张，有效期至本月底。', 'COUPON', NULL, 1, DATE_SUB(NOW(3), INTERVAL 2 DAY)),
(9002, 'SYSTEM', '服务时间调整通知', '11 月 11 日客服在线时间调整为 08:00-24:00，下单与发货不受影响。', NULL, NULL, 1, DATE_SUB(NOW(3), INTERVAL 3 DAY)),
(9002, 'PROMO',  '积分即将到期', '您有 120 积分将于本月底到期，可在下单时抵扣现金。', 'COUPON', NULL, 1, DATE_SUB(NOW(3), INTERVAL 5 DAY));
