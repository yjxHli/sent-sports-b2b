package com.sent.sports.chat;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.domain.SessionType;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.BotConversationUseCase;
import com.sent.sports.chat.service.BotReplyUseCase;
import com.sent.sports.chat.service.ChatNotifier;
import com.sent.sports.chat.service.SessionAssignmentUseCase;
import com.sent.sports.chat.service.dto.BotReplyOutcome;
import com.sent.sports.chat.service.dto.BotTurnResult;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 机器人一轮对话的落库与推送测试（2026-11-24，TDD 红）。
 *
 * <p>关键口径：客户消息与机器人回复都要落库（否则一次 history 刷新就把回复抹掉）；
 * 转人工时才让坐席看到这条消息；命中知识库时不打扰坐席。</p>
 */
class BotConversationUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_700_000_000_000L);

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private BotReplyUseCase botReplyUseCase;
    @Mock
    private ChatNotifier notifier;
    @Mock
    private SessionAssignmentUseCase assignmentUseCase;
    @Mock
    private IdGen idGen;
    @Mock
    private Clock clock;
    @InjectMocks
    private BotConversationUseCase useCase;

    private ChatSession botSession() {
        return new ChatSession(7L, 9002L, false, ChatSessionStatus.WAITING, null, 1L, 1L, 0, SessionType.BOT);
    }

    @Test
    @DisplayName("人工会话不接管：返回 notHandled，一条都不落库")
    void humanSessionIsNotHandled() {
        ChatSession human = new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L);
        when(botReplyUseCase.decide(human, "在吗")).thenReturn(BotReplyOutcome.none());

        BotTurnResult result = useCase.handle(human, "在吗", "c-1");

        assertFalse(result.handled());
        verify(messageRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("命中知识库：客户消息 + 机器人回复都落库（回复发送方是 BOT），推一条机器人帧，不通知坐席")
    void faqHitPersistsBothSidesAndPushesBotFrame() {
        ChatSession session = botSession();
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("MSG")).thenReturn("MSG-C", "MSG-BOT");
        when(botReplyUseCase.decide(session, "什么时候发货"))
                .thenReturn(new BotReplyOutcome(BotReplyOutcome.BotAction.REPLY, "亲，我们默认发顺丰，48小时内发货哦~"));

        BotTurnResult result = useCase.handle(session, "什么时候发货", "c-9");

        assertTrue(result.handled());
        assertFalse(result.notifyStaff(), "机器人答上来了就不该把消息丢给坐席");
        assertEquals(1, result.customerSeq());
        assertEquals(2, result.replySeq());
        assertEquals("MSG-C", result.customerMsgId());

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(2)).save(saved.capture());
        List<ChatMessage> messages = saved.getAllValues();
        assertEquals(SenderType.CUSTOMER, messages.get(0).getSenderType());
        assertEquals("什么时候发货", messages.get(0).getContent());
        assertEquals(SenderType.BOT, messages.get(1).getSenderType(),
                "机器人回复必须落成 BOT：客户端按发送方渲染机器人头像气泡，SYSTEM 只会变成居中灰字");
        assertEquals("亲，我们默认发顺丰，48小时内发货哦~", messages.get(1).getContent());
        assertEquals(2, messages.get(1).getSeq());

        verify(notifier).systemMessage(9002L, 7L, 2, "亲，我们默认发顺丰，48小时内发货哦~", SenderType.BOT);
        assertTrue(session.isBot(), "命中知识库不该改接待模式，会话仍由机器人接待");
    }

    @Test
    @DisplayName("转人工指令：会话切 HUMAN + WAITING，回复落库成 BOT 并提示客户")
    void transferCommandFlipsSessionToHuman() {
        ChatSession session = botSession();
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("MSG")).thenReturn("MSG-C", "MSG-TIP");
        when(botReplyUseCase.decide(session, "转人工"))
                .thenReturn(new BotReplyOutcome(BotReplyOutcome.BotAction.TRANSFER, BotReplyUseCase.TRANSFER_TIP));

        BotTurnResult result = useCase.handle(session, "转人工", "c-10");

        assertTrue(result.notifyStaff(), "转人工后坐席要能看到客户说了什么");
        assertEquals(SessionType.HUMAN, session.getSessionType());
        assertEquals(ChatSessionStatus.WAITING, session.getStatus());
        verify(sessionRepository).save(session);
        verify(notifier).transferStarted(9002L, 7L, 2, BotReplyUseCase.TRANSFER_TIP, SenderType.BOT);
        verify(notifier, never()).systemMessage(anyLong(), anyLong(), anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("答不上来：切 HUMAN 但只落客户消息，不推任何提示（与「已接入」提示不重叠）")
    void handoffPersistsCustomerOnly() {
        ChatSession session = botSession();
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("MSG")).thenReturn("MSG-C");
        when(botReplyUseCase.decide(session, "你们老板姓什么"))
                .thenReturn(new BotReplyOutcome(BotReplyOutcome.BotAction.HANDOFF, null));

        BotTurnResult result = useCase.handle(session, "你们老板姓什么", "c-11");

        assertTrue(result.notifyStaff());
        assertEquals(0, result.replySeq());
        assertEquals(SessionType.HUMAN, session.getSessionType());
        assertEquals(1, session.getSeq(), "只消耗客户消息一格序号");
        verify(messageRepository, times(1)).save(any());
        verify(notifier, never()).systemMessage(anyLong(), anyLong(), anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("转人工后立刻找空位接入：坐席已在线时不必等客服手动点「接待」")
    void transferTriggersAutoAssignment() {
        ChatSession session = botSession();
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("MSG")).thenReturn("MSG-C", "MSG-TIP");
        when(botReplyUseCase.decide(session, "转人工"))
                .thenReturn(new BotReplyOutcome(BotReplyOutcome.BotAction.TRANSFER, BotReplyUseCase.TRANSFER_TIP));

        useCase.handle(session, "转人工", "c-12");

        // 会话切 HUMAN + WAITING 之后必须尝试自动分配——否则「建会话时分配」那条链路永远不会被触发，
        // 在线坐席看到的只是一个空荡荡的进行中列表，客户却卡在「前方还有 0 人」。
        verify(assignmentUseCase).assignIfCapacity(session);
    }

    @Test
    @DisplayName("答不上来（静默转人工）同样要找空位接入")
    void handoffTriggersAutoAssignment() {
        ChatSession session = botSession();
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("MSG")).thenReturn("MSG-C");
        when(botReplyUseCase.decide(session, "你们老板姓什么"))
                .thenReturn(new BotReplyOutcome(BotReplyOutcome.BotAction.HANDOFF, null));

        useCase.handle(session, "你们老板姓什么", "c-13");

        verify(assignmentUseCase).assignIfCapacity(session);
    }

    @Test
    @DisplayName("机器人答上来的会话不找坐席：仍由机器人接待")
    void faqReplyDoesNotAssign() {
        ChatSession session = botSession();
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("MSG")).thenReturn("MSG-C", "MSG-B");
        when(botReplyUseCase.decide(session, "什么时候发货"))
                .thenReturn(new BotReplyOutcome(BotReplyOutcome.BotAction.REPLY, "48小时内发货"));

        useCase.handle(session, "什么时候发货", "c-14");

        verify(assignmentUseCase, never()).assignIfCapacity(any());
    }

    @Test
    @DisplayName("机器人回复的客户消息也带 clientMsgId，客户端据此对齐乐观气泡")
    void customerMessageKeepsClientMsgId() {
        ChatSession session = botSession();
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("MSG")).thenReturn("MSG-C", "MSG-R");
        when(botReplyUseCase.decide(any(), anyString()))
                .thenReturn(new BotReplyOutcome(BotReplyOutcome.BotAction.REPLY, "答复"));

        useCase.handle(session, "你好", "ws_abc");

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(2)).save(saved.capture());
        assertEquals("ws_abc", saved.getAllValues().get(0).getClientMsgId());
        assertEquals("BOT-ws_abc", saved.getAllValues().get(1).getClientMsgId(),
                "机器人回复的 clientMsgId 要与客户消息区分，否则 uk_msg_client 会撞");
    }
}
