package com.sent.sports.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatMsgType;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;

/**
 * 发送「请评价本次服务」卡片（2026-11-17 用户要求）。
 * <p>两个触发点：客服主动点按钮；会话结束时由 {@code CloseSessionUseCase} 在关闭前自动补发。
 * <p>幂等键固定为 {@code SATISFY-{sessionId}}——同一会话永远只有一张卡片，
 * 客服重复点、自动补发撞人工发卡都只会返回原消息，不会刷屏。
 * <p>关闭前发而不是关闭后发：{@link ChatSession#appendMessage()} 拒绝 CLOSED 会话，
 * 卡片必须赶在状态迁移之前落库；客户在会话结束后仍可点卡片打分。
 */
@Service
public class SendSatisfactionCardUseCase {

    /** 评价卡固定幂等键，也是两端消息卡片的稳定 key。 */
    public static String clientMsgId(long sessionId) {
        return "SATISFY-" + sessionId;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final IdGen idGen;
    private final Clock clock;
    private final ChatNotifier notifier;

    public SendSatisfactionCardUseCase(ChatSessionRepository sessionRepository,
                                       ChatMessageRepository messageRepository, IdGen idGen,
                                       Clock clock, ChatNotifier notifier) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.idGen = idGen;
        this.clock = clock;
        this.notifier = notifier;
    }

    /** @return 卡片消息 ID；会话不存在抛 NOT_FOUND */
    @UseCase
    public String send(long sessionId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));

        String clientMsgId = clientMsgId(sessionId);
        var existing = messageRepository.findBySessionAndClientMsgId(sessionId, clientMsgId);
        if (existing.isPresent()) {
            return existing.get().getMessageId();
        }

        int seq = session.appendMessage();
        long now = clock.instant().toEpochMilli();
        String messageId = idGen.next("CM");
        ChatMessage card = new ChatMessage(messageId, sessionId, SenderType.SYSTEM,
                json("PENDING", null), clientMsgId, seq, now, ChatMsgType.SATISFY);
        messageRepository.save(card);
        // 卡片也要刷新会话活跃时间：否则客户只是收到评价请求却被 10 分钟超时任务收掉
        session.touch(now);
        sessionRepository.save(session);
        notifier.messagePublished(session, card);
        return messageId;
    }

    /** 打分后把原卡片就地改成 RATED（不新增 seq、不刷屏）。 */
    static String ratedContent(String score) {
        return json("RATED", score);
    }

    private static String json(String status, String score) {
        try {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("status", status);
            if (score != null) {
                body.put("score", score);
            }
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("评价卡 JSON 序列化失败", e);
        }
    }

    /** 供测试断言的静态内容构造（不改可见性也能复用）。 */
    public static String pendingContent() {
        return json("PENDING", null);
    }
}
