package com.sent.sports.chat.domain;

/**
 * 会话消息实体（plan §7.8 / spec §6.10）：纯 POJO。
 * seq 在同一会话内自增，clientMsgId 用于幂等去重。
 */
public class ChatMessage {

    private final String messageId;
    private final long sessionId;
    private final SenderType senderType;
    private final String content;
    private final String clientMsgId;
    private final int seq;
    private final long createdAtMillis;

    public ChatMessage(String messageId, long sessionId, SenderType senderType, String content,
                       String clientMsgId, int seq, long createdAtMillis) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.senderType = senderType;
        this.content = content;
        this.clientMsgId = clientMsgId;
        this.seq = seq;
        this.createdAtMillis = createdAtMillis;
    }

    public String getMessageId() {
        return messageId;
    }

    public long getSessionId() {
        return sessionId;
    }

    public SenderType getSenderType() {
        return senderType;
    }

    public String getContent() {
        return content;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public int getSeq() {
        return seq;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
}
