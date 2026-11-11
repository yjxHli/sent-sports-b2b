package com.sent.sports.chat.service.dto;

import com.sent.sports.chat.domain.SenderType;

/**
 * 发送消息命令（中立入参）：会话号、发送方、内容、客户端消息号(幂等)、操作人。
 */
public record SendMessageCmd(long sessionId, SenderType senderType, String content,
                             String clientMsgId, long operatorId) {
}
