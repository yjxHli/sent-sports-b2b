package com.sent.sports.chat;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.FaqKnowledge;
import com.sent.sports.chat.domain.SessionType;
import com.sent.sports.chat.repository.FaqKnowledgeRepository;
import com.sent.sports.chat.service.BotReplyUseCase;
import com.sent.sports.chat.service.dto.BotReplyOutcome;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 机器人应答决策测试（2026-11-24，TDD 红）。
 *
 * <p>四档：非 BOT 不管 / 转人工指令 / 知识库命中 / 答不上来转人工。</p>
 */
class BotReplyUseCaseTest extends BaseUseCaseTest {

    @Mock
    private FaqKnowledgeRepository faqKnowledgeRepository;
    @InjectMocks
    private BotReplyUseCase useCase;

    private ChatSession botSession() {
        return new ChatSession(1L, 9002L, false, ChatSessionStatus.WAITING, null, 0L, 0L, 0, SessionType.BOT);
    }

    private ChatSession humanSession() {
        return new ChatSession(1L, 9002L, ChatSessionStatus.ACTIVE, 9001L);
    }

    @Test
    @DisplayName("人工会话不归机器人管：返回 NONE，调用方走原链路")
    void humanSessionIsNotHandled() {
        BotReplyOutcome outcome = useCase.decide(humanSession(), "你们发什么快递");

        assertEquals(BotReplyOutcome.BotAction.NONE, outcome.action());
        assertFalse(outcome.handled());
        verify(faqKnowledgeRepository, never()).findAllEnabledOrdered();
    }

    @Test
    @DisplayName("命中知识库：回复原文，留在机器人模式，不转人工")
    void faqHitReplies() {
        when(faqKnowledgeRepository.findAllEnabledOrdered()).thenReturn(List.of(
                new FaqKnowledge(3L, "发货,物流,快递", "亲，我们默认发顺丰，48小时内发货哦~", 0)));

        BotReplyOutcome outcome = useCase.decide(botSession(), "什么时候发货");

        assertEquals(BotReplyOutcome.BotAction.REPLY, outcome.action());
        assertEquals("亲，我们默认发顺丰，48小时内发货哦~", outcome.reply());
        assertFalse(outcome.handoff(), "命中知识库不该把客户丢进人工队列");
        verify(faqKnowledgeRepository).recordHit(3L);
    }

    @Test
    @DisplayName("转人工指令：TRANSFER，回复固定提示语，不去查知识库")
    void transferCommandSwitchesToHuman() {
        for (String command : List.of("转人工", "人工", "我要找人工客服", "转人工谢谢", "真人")) {
            BotReplyOutcome outcome = useCase.decide(botSession(), command);

            assertEquals(BotReplyOutcome.BotAction.TRANSFER, outcome.action(), "「" + command + "」应识别为转人工");
            assertEquals(BotReplyUseCase.TRANSFER_TIP, outcome.reply());
            assertTrue(outcome.handoff());
        }
        verify(faqKnowledgeRepository, never()).findAllEnabledOrdered();
    }

    @Test
    @DisplayName("答不上来：HANDOFF 静默转人工（不硬答、也不额外说话）")
    void unmatchedMessageHandsOffSilently() {
        when(faqKnowledgeRepository.findAllEnabledOrdered()).thenReturn(List.of(
                new FaqKnowledge(3L, "发货,物流", "发货答复", 0)));

        BotReplyOutcome outcome = useCase.decide(botSession(), "你们老板姓什么");

        assertEquals(BotReplyOutcome.BotAction.HANDOFF, outcome.action());
        assertTrue(outcome.handoff());
        assertEquals(null, outcome.reply(), "静默转人工不能再补一句话，否则会与「已接入」提示重叠");
        verify(faqKnowledgeRepository, never()).recordHit(3L);
    }

    @Test
    @DisplayName("知识库为空时也算答不上来，不能抛异常把消息吞掉")
    void emptyDictionaryHandsOff() {
        when(faqKnowledgeRepository.findAllEnabledOrdered()).thenReturn(List.of());

        BotReplyOutcome outcome = useCase.decide(botSession(), "发货了吗");

        assertEquals(BotReplyOutcome.BotAction.HANDOFF, outcome.action());
        verify(faqKnowledgeRepository, times(1)).findAllEnabledOrdered();
    }

    @Test
    @DisplayName("文本为空白时按转人工处理，不查库")
    void blankContentHandsOff() {
        BotReplyOutcome outcome = useCase.decide(botSession(), "   ");

        assertEquals(BotReplyOutcome.BotAction.HANDOFF, outcome.action());
        verify(faqKnowledgeRepository, never()).findAllEnabledOrdered();
    }
}
