package com.sent.sports.chat.service.dto;

import com.sent.sports.chat.domain.ChatMsgType;
import com.sent.sports.chat.domain.SenderType;

/**
 * 发送消息命令（中立入参）：会话号、发送方、内容、客户端消息号(幂等)、操作人、消息类型。
 *
 * @param msgType TEXT（默认）/ IMAGE；IMAGE 时 {@code content} 为文件服务返回的 fileId
 */
public record SendMessageCmd(long sessionId, SenderType senderType, String content,
                             String clientMsgId, long operatorId, String msgType) {

    /** 兼容旧调用：不带 msgType 时按 TEXT 处理。 */
    public SendMessageCmd(long sessionId, SenderType senderType, String content,
                          String clientMsgId, long operatorId) {
        this(sessionId, senderType, content, clientMsgId, operatorId, ChatMsgType.TEXT.name());
    }
}
