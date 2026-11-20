package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatMsgType;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.dto.SendMessageCmd;
import com.sent.sports.chat.service.dto.SendMessageResult;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.common.util.SensitiveWordFilter;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 发送消息用例（P3-52/53）：spec §6.10——
 * 会话已结束(CLOSED)拒绝(STATE_CONFLICT)；同 clientMsgId 幂等返回原 seq；seq 会话内递增。
 * 仅依赖 chat 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class SendMessageUseCase {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final IdGen idGen;
    private final Clock clock;
    private final ChatNotifier notifier;
    private final SensitiveWordFilter sensitiveWordFilter;

    public SendMessageUseCase(ChatSessionRepository sessionRepository,
                              ChatMessageRepository messageRepository, IdGen idGen, Clock clock,
                              ChatNotifier notifier, SensitiveWordFilter sensitiveWordFilter) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.idGen = idGen;
        this.clock = clock;
        this.notifier = notifier;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    @UseCase
    public SendMessageResult send(SendMessageCmd cmd) {
        ChatSession session = sessionRepository.findById(cmd.sessionId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }

        Optional<ChatMessage> existing = messageRepository.findBySessionAndClientMsgId(
                cmd.sessionId(), cmd.clientMsgId());
        if (existing.isPresent()) {
            ChatMessage m = existing.get();
            return new SendMessageResult(m.getMessageId(), m.getSeq(), cmd.sessionId());
        }

        ChatMsgType msgType = ChatMsgType.of(cmd.msgType());
        // 敏感词风控（2026-11-19）：命中直接阻断，不打码、不落库、不推送。
        // 校验必须在 appendMessage/touch 之前——先改会话状态再拒绝，会话序号会被白白消耗一格。
        // 只检 TEXT：IMAGE 的 content 是文件服务 fileId、ORDER 是订单摘要 JSON，
        // 拿它们做分词匹配只会误杀正常业务消息。
        if (msgType == ChatMsgType.TEXT && sensitiveWordFilter.contains(cmd.content())) {
            throw new BizException(ErrorCode.SENSITIVE_MESSAGE);
        }
        long now = clock.instant().toEpochMilli();
        int seq = session.appendMessage();
        session.touch(now);
        String messageId = idGen.next("CM");
        ChatMessage msg = new ChatMessage(messageId, cmd.sessionId(), cmd.senderType(), cmd.content(),
                cmd.clientMsgId(), seq, now, msgType);
        messageRepository.save(msg);
        sessionRepository.save(session);
        // REST 兜底发送（用户端 WS 断开时走这条）也必须推送，否则客服端要刷新才看得到
        notifier.messagePublished(session, msg);
        return new SendMessageResult(messageId, seq, cmd.sessionId());
    }
}
