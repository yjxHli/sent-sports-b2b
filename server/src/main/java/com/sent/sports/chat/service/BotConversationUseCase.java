package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatMsgType;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.dto.BotReplyOutcome;
import com.sent.sports.chat.service.dto.BotTurnResult;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.util.IdGen;

import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * 机器人一轮对话的落库与推送（2026-11-24）。
 *
 * <p>WS 上行与 REST 兜底两条链路共用这一个实现：机器人接管时，客户消息与机器人回复<b>都要落库</b>，
 * 只推帧不落库的话，客户端一次 history 刷新就会把机器人说的话抹掉（表现为「刚回过我，一刷新没了」）。</p>
 *
 * <p>转人工（TRANSFER / HANDOFF）时先切 HUMAN 再落机器人回复：
 * 这样「正在为您转接…」这条提示既进了历史，也在切完类型之后发出，
 * 排队座位号（countWaitingBefore 只数 HUMAN 会话）从这一刻才开始算。</p>
 */
@Service
public class BotConversationUseCase {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final BotReplyUseCase botReplyUseCase;
    private final ChatNotifier notifier;
    private final SessionAssignmentUseCase assignmentUseCase;
    private final IdGen idGen;
    private final Clock clock;

    public BotConversationUseCase(ChatSessionRepository sessionRepository,
                                  ChatMessageRepository messageRepository,
                                  BotReplyUseCase botReplyUseCase,
                                  ChatNotifier notifier,
                                  SessionAssignmentUseCase assignmentUseCase,
                                  IdGen idGen,
                                  Clock clock) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.botReplyUseCase = botReplyUseCase;
        this.notifier = notifier;
        this.assignmentUseCase = assignmentUseCase;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public BotTurnResult handle(ChatSession session, String content, String clientMsgId) {
        BotReplyOutcome outcome = botReplyUseCase.decide(session, content);
        if (!outcome.handled()) {
            return BotTurnResult.notHandled();
        }
        long now = clock.instant().toEpochMilli();

        int customerSeq = session.appendMessage();
        session.touch(now);
        String customerMsgId = idGen.next("MSG");
        messageRepository.save(new ChatMessage(customerMsgId, session.getId(), SenderType.CUSTOMER,
                content, clientMsgId, customerSeq, now, ChatMsgType.TEXT));

        boolean notifyStaff = outcome.handoff();
        if (notifyStaff) {
            session.transferToHuman();
        }

        int replySeq = 0;
        if (outcome.reply() != null && !outcome.reply().isBlank()) {
            replySeq = session.appendMessage();
            // 发送方是 BOT 而不是 SYSTEM（2026-11-24 用户要求）：机器人说的是「有人格的话」，
            // 客户端要渲染成带机器人头像的普通气泡；SYSTEM 保留给「会话已结束」这类旁白。
            messageRepository.save(new ChatMessage(idGen.next("MSG"), session.getId(), SenderType.BOT,
                    outcome.reply(), "BOT-" + clientMsgId, replySeq, now, ChatMsgType.TEXT));
        }
        sessionRepository.save(session);

        if (replySeq > 0) {
            // 转人工用专用帧（event=TRANSFER），前端据此切回排队口径；其余走通用提示帧。
            // 两种帧都带 senderType=BOT：客户侧一律按机器人气泡渲染（转接提示也是机器人说的）。
            if (outcome.action() == BotReplyOutcome.BotAction.TRANSFER) {
                notifier.transferStarted(session.getCustomerId(), session.getId(), replySeq, outcome.reply(),
                        SenderType.BOT);
            } else {
                notifier.systemMessage(session.getCustomerId(), session.getId(), replySeq, outcome.reply(),
                        SenderType.BOT);
            }
        }
        // 转人工后立刻找空位接入（2026-11-25）：放在提示帧之后，客户先看到「正在为您转接」，
        // 紧接着才是「客服已接入」，顺序反过来会让「转接中」显得像马后炮。
        if (notifyStaff) {
            assignmentUseCase.assignIfCapacity(session);
        }
        return new BotTurnResult(true, notifyStaff, customerMsgId, customerSeq, replySeq);
    }
}
