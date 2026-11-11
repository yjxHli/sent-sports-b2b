package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatMessage;
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

    public SendMessageUseCase(ChatSessionRepository sessionRepository,
                              ChatMessageRepository messageRepository, IdGen idGen, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.idGen = idGen;
        this.clock = clock;
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

        long now = clock.instant().toEpochMilli();
        int seq = session.appendMessage();
        session.touch(now);
        String messageId = idGen.next("CM");
        ChatMessage msg = new ChatMessage(messageId, cmd.sessionId(), cmd.senderType(), cmd.content(),
                cmd.clientMsgId(), seq, now);
        messageRepository.save(msg);
        sessionRepository.save(session);
        return new SendMessageResult(messageId, seq, cmd.sessionId());
    }
}
