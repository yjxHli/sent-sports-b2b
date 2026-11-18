package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatMsgType;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.domain.SatisfactionScore;
import com.sent.sports.chat.dto.SatisfactionDtos.RateResult;
import com.sent.sports.chat.mapper.ChatSatisfactionMapper;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 客户给刚刚服务的客服打分（2026-11-17 用户要求）。
 * <p>口径：满意率 = 满意数 ÷ 有效评价数；一般/不满意都算有效评价（进分母）。
 * <p>幂等唯一真源 = DB 的 {@code uk_satisfaction_session}：重复点击、并发提交都只留一条，
 * 已评价时直接回原档位，不引入分布式锁。会话结束后仍允许打分（客户是点结束后的卡片评分）。
 */
@Service
public class RateSatisfactionUseCase {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSatisfactionMapper satisfactionMapper;
    private final ChatNotifier notifier;

    public RateSatisfactionUseCase(ChatSessionRepository sessionRepository,
                                   ChatMessageRepository messageRepository,
                                   ChatSatisfactionMapper satisfactionMapper,
                                   ChatNotifier notifier) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.satisfactionMapper = satisfactionMapper;
        this.notifier = notifier;
    }

    @UseCase
    public RateResult rate(long sessionId, String rawScore) {
        SatisfactionScore score;
        try {
            score = SatisfactionScore.of(rawScore);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评价档位只能是满意 / 一般 / 不满意");
        }

        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (session.getStaffId() == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该会话尚未有客服接待，无需评价");
        }

        String clientMsgId = SendSatisfactionCardUseCase.clientMsgId(sessionId);
        var card = messageRepository.findBySessionAndClientMsgId(sessionId, clientMsgId);

        // 先查再插：正常重复点击直接返回；并发撞唯一索引时由 catch 再查一次兜底
        String existing = satisfactionMapper.selectScoreBySession(sessionId);
        if (existing != null) {
            return new RateResult(sessionId, existing, card.map(ChatMessage::getMessageId).orElse(null));
        }

        String messageId = card.map(ChatMessage::getMessageId).orElse(null);
        try {
            satisfactionMapper.insert(sessionId, session.getStaffId(), session.getCustomerId(),
                    score.name(), messageId);
        } catch (DuplicateKeyException e) {
            existing = satisfactionMapper.selectScoreBySession(sessionId);
            return new RateResult(sessionId, existing == null ? score.name() : existing, messageId);
        }

        // 原卡片就地改成 RATED：不新增消息、不打断 seq，两端看到同一张卡变化
        if (card.isPresent()) {
            ChatMessage old = card.get();
            messageRepository.updateContent(old.getMessageId(),
                    SendSatisfactionCardUseCase.ratedContent(score.name()));
            ChatMessage updated = new ChatMessage(old.getMessageId(), sessionId, SenderType.SYSTEM,
                    SendSatisfactionCardUseCase.ratedContent(score.name()), clientMsgId,
                    old.getSeq(), old.getCreatedAtMillis(), ChatMsgType.SATISFY);
            // 只广播刷新，不落库：这条是「卡片状态更新帧」，不是新消息
            notifier.messagePublished(session, updated);
        }
        return new RateResult(sessionId, score.name(), messageId);
    }
}
