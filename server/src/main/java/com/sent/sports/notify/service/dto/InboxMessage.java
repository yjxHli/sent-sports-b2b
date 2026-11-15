package com.sent.sports.notify.service.dto;

/**
 * 待投递的站内信（跨模块写入口的中立模型）。
 *
 * <p>放 service 子包而不是 mapper/domain：其他模块（退款、订单……）只允许依赖本站的
 * {@code notify.service} + 其 DTO，不得碰 mapper/domain（ArchUnit 规则 4）。</p>
 *
 * @param customerId 收件客户 ID
 * @param msgType    ORDER 订单 / PROMO 优惠 / SYSTEM 系统
 * @param title      标题（消息中心列表首行）
 * @param content    正文
 * @param bizType    点进去要跳的业务类型：ORDER / CUSTOM / COUPON / REFUND，可空
 * @param bizNo      业务单号，可空
 */
public record InboxMessage(long customerId, String msgType, String title, String content,
                           String bizType, String bizNo) {
}
