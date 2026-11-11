-- V20：退款单增加申诉次数（仅允许一次，超过则拒绝），支撑用户端售后详情与申诉链路。
ALTER TABLE refund_order
    ADD COLUMN appeal_count INT NOT NULL DEFAULT 0 COMMENT '申诉次数（仅一次，超过则拒绝）';
