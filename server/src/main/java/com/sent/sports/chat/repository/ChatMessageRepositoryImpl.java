package com.sent.sports.chat.repository;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.mapper.ChatMessageMapper;
import com.sent.sports.chat.mapper.po.ChatMessagePO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 消息仓储实现（P5-22）。
 * <p>落库的幂等由数据库唯一键兜底（{@code uk_msg_client}）：同一会话同一客户端消息号重复插入会抛
 * {@link DuplicateKeyException}，由用例层catch后返回已存在消息，从而实现「客户端重发不重复显示」。
 */
@Repository
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageMapper chatMessageMapper;

    public ChatMessageRepositoryImpl(ChatMessageMapper chatMessageMapper) {
        this.chatMessageMapper = chatMessageMapper;
    }

    @Override
    public void save(ChatMessage message) {
        ChatMessagePO po = new ChatMessagePO();
        po.setMessageNo(message.getMessageId());
        po.setSessionId(message.getSessionId());
        po.setSeq((long) message.getSeq());
        po.setSenderType(message.getSenderType().name());
        po.setMsgType("TEXT");
        po.setContent(message.getContent());
        po.setClientMsgId(message.getClientMsgId());
        chatMessageMapper.insert(po);
    }

    @Override
    public Optional<ChatMessage> findBySessionAndClientMsgId(long sessionId, String clientMsgId) {
        ChatMessagePO po = chatMessageMapper.selectByClientMsgId(sessionId, clientMsgId);
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    @Override
    public List<ChatMessage> findBySessionId(long sessionId) {
        List<ChatMessagePO> pos = chatMessageMapper.selectBySessionId(sessionId);
        List<ChatMessage> list = new ArrayList<>(pos.size());
        for (ChatMessagePO po : pos) {
            list.add(toDomain(po));
        }
        return list;
    }

    private ChatMessage toDomain(ChatMessagePO po) {
        return new ChatMessage(po.getMessageNo(), po.getSessionId(),
                SenderType.valueOf(po.getSenderType()), po.getContent(), po.getClientMsgId(),
                po.getSeq() == null ? 0 : po.getSeq().intValue(), toMillis(po.getCreatedAt()));
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 供后续补拉工具使用。 */
    static LocalDateTime fromMillis(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
