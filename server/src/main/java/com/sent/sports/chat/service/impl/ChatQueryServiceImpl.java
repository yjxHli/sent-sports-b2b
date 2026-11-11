package com.sent.sports.chat.service.impl;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.dto.ChatDtos;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.service.ChatQueryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 客服会话查询实现（P5 收口）：历史消息按 {@code afterSeq} 增量拉取。
 * <p>原为 P4 占位桩（恒返回空列表）：会让用户端「发完消息再刷新历史」把气泡清空，
 * 断线补拉也永远拿不到消息，实时链路看起来像「没收到」。现改为读真实仓储。</p>
 * <p>已读上报仍为无操作：{@code chat_session} 暂无已读游标列，落库需追加迁移，本期不做。</p>
 */
@Service
public class ChatQueryServiceImpl implements ChatQueryService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatQueryServiceImpl(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    public List<ChatDtos.MessageVO> history(long sessionId, int afterSeq) {
        List<ChatMessage> all = chatMessageRepository.findBySessionId(sessionId);
        List<ChatDtos.MessageVO> result = new ArrayList<>(all.size());
        for (ChatMessage m : all) {
            if (m.getSeq() <= afterSeq) {
                continue;
            }
            result.add(new ChatDtos.MessageVO(m.getMessageId(), m.getSessionId(),
                    m.getSenderType() == null ? null : m.getSenderType().name(),
                    m.getContent(), m.getClientMsgId(), m.getSeq(), m.getCreatedAtMillis()));
        }
        return result;
    }

    @Override
    public void reportRead(long sessionId, int seq) {
        // 占位：会话表暂无已读游标列，已读由前端本地推进
    }
}
