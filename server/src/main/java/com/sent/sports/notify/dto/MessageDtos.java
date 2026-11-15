package com.sent.sports.notify.dto;

/**
 * 站内信对外模型。
 */
public final class MessageDtos {

    private MessageDtos() {
    }

    /** 消息列表项：{@code createdAt} 为毫秒，前端统一按毫秒渲染。 */
    public record MessageVO(Long id, String msgType, String title, String content,
                            String bizType, String bizNo, boolean read, Long createdAt) {
    }

    /** 未读数（顶栏红点）。 */
    public record UnreadCountVO(long unreadCount) {
    }
}
