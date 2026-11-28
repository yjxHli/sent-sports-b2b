package com.sent.sports.chat.service.impl;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.dto.ChatDtos;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.ChatQueryService;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 客服会话查询实现（P5 收口）：历史消息按 {@code afterSeq} 增量拉取。
 * <p>原为 P4 占位桩（恒返回空列表）：会让用户端「发完消息再刷新历史」把气泡清空，
 * 断线补拉也永远拿不到消息，实时链路看起来像「没收到」。现改为读真实仓储。</p>
 * <p>已读上报仍为无操作：{@code chat_session} 暂无已读游标列，落库需追加迁移，本期不做。</p>
 * <p>排队位置读 {@code chat_session}：前方 WAITING 数量 + 1，用户端据此显示「前方还有 X 人」。</p>
 */
@Service
public class ChatQueryServiceImpl implements ChatQueryService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;

    public ChatQueryServiceImpl(ChatMessageRepository chatMessageRepository,
                                ChatSessionRepository chatSessionRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionRepository = chatSessionRepository;
    }

    @Override
    public List<ChatDtos.MessageVO> history(long sessionId, int afterSeq, Long viewerCustomerId) {
        // P0-04：客户只能拉自己会话的消息。校验在读取消息之前——越权请求连 SQL 都不跑，
        // 避免「先查出内容再判权」这种把数据带进内存的写法。
        if (viewerCustomerId != null) {
            ChatSession session = chatSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "会话不存在"));
            if (session.getCustomerId() != viewerCustomerId) {
                throw new BizException(ErrorCode.FORBIDDEN, "无权查看该会话");
            }
        }
        List<ChatMessage> all = chatMessageRepository.findBySessionId(sessionId);
        List<ChatDtos.MessageVO> result = new ArrayList<>(all.size());
        for (ChatMessage m : all) {
            if (m.getSeq() <= afterSeq) {
                continue;
            }
            result.add(new ChatDtos.MessageVO(m.getMessageId(), m.getSessionId(),
                    m.getSenderType() == null ? null : m.getSenderType().name(),
                    m.getMsgType().name(),
                    m.getContent(), m.getClientMsgId(), m.getSeq(), m.getCreatedAtMillis()));
        }
        return result;
    }

    @Override
    public void reportRead(long sessionId, int seq) {
        // 占位：会话表暂无已读游标列，已读由前端本地推进
    }

    @Override
    public ChatDtos.SessionVO currentOpen(long customerId) {
        return chatSessionRepository.findByCustomerId(customerId).stream()
                .filter(s -> s.getStatus() != com.sent.sports.chat.domain.ChatSessionStatus.CLOSED)
                .findFirst()
                .map(s -> new ChatDtos.SessionVO(s.getId(), s.getStatus().name(), s.getStaffId(), true,
                        s.getSessionType().name()))
                .orElse(null);
    }

    @Override
    public List<ChatDtos.SessionSummaryVO> listSessions(long customerId, int limit) {
        if (customerId <= 0) {
            return List.of();
        }
        int size = Math.max(1, Math.min(limit, 50));
        return chatSessionRepository.findByCustomerId(customerId).stream()
                .limit(size)
                .map(s -> new ChatDtos.SessionSummaryVO(
                        s.getId(), s.getStatus().name(), s.getStaffId(),
                        s.getCreatedAtMillis(), s.getLastActiveAtMillis()))
                .toList();
    }

    @Override
    public ChatDtos.QueueVO queue(long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (session.getStatus() != ChatSessionStatus.WAITING) {
            return new ChatDtos.QueueVO(session.getId(), session.getStatus().name(), 0, session.getStaffId());
        }
        int ahead = chatSessionRepository.countWaitingBefore(session.getId(), session.getCreatedAtMillis());
        return new ChatDtos.QueueVO(session.getId(), session.getStatus().name(), ahead + 1, session.getStaffId());
    }
}
