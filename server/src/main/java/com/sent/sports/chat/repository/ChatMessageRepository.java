package com.sent.sports.chat.repository;

import com.sent.sports.chat.domain.ChatMessage;

import java.util.List;
import java.util.Optional;

/**
 * 会话消息仓储端口（plan §7.8）：实现见 Phase 5。
 */
public interface ChatMessageRepository {

    /** 持久化消息。 */
    void save(ChatMessage message);

    /** 幂等去重：按 (会话, 客户端消息号) 查询已存在消息。 */
    Optional<ChatMessage> findBySessionAndClientMsgId(long sessionId, String clientMsgId);

    /** 某会话的全部消息（按 seq 升序由实现保证），用于接收方查看历史。 */
    List<ChatMessage> findBySessionId(long sessionId);
}
