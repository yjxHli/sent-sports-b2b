package com.sent.sports.chat;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatMsgType;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.ChatNotifier;
import com.sent.sports.chat.service.SendMessageUseCase;
import com.sent.sports.chat.service.dto.SendMessageCmd;
import com.sent.sports.chat.service.dto.SendMessageResult;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发送消息用例测试（P3-52/53，TDD 红）：幂等(同 clientMsgId 返回原 seq)、
 * 会话已结束(CLOSED)拒绝(STATE_CONFLICT)、seq 递增。
 */
class SendMessageUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private Clock clock;
    @Mock
    private ChatNotifier notifier;
    @InjectMocks
    private SendMessageUseCase useCase;
    @Captor
    private ArgumentCaptor<ChatMessage> messageCaptor;

    /**
     * 回归：REST 兜底发送（用户端 WS 断开时走这条）也必须推送，否则客服端要刷新才看得到。
     */
    @Test
    void restSendAlsoPublishesToStaff() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionAndClientMsgId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(idGen.next("CM")).thenReturn("CM-1");
        when(clock.instant()).thenReturn(NOW);

        useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "在吗", "c-1", 1L));

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(saved.capture());
        verify(notifier).messagePublished(session, saved.getValue());
    }

    @Test
    void incrementsSeqForEachNewMessage() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionAndClientMsgId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("CM")).thenReturn("CM1");

        SendMessageResult r1 = useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "c1", 1L));
        SendMessageResult r2 = useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "again", "c2", 1L));

        assertEquals(1, r1.seq());
        assertEquals(2, r2.seq());
        verify(messageRepository, times(2)).save(messageCaptor.capture());
        ChatMessage last = messageCaptor.getValue();
        assertEquals("CM1", last.getMessageId());
        assertEquals(1L, last.getSessionId());
        assertEquals(SenderType.CUSTOMER, last.getSenderType());
        assertEquals("again", last.getContent());
        assertEquals("c2", last.getClientMsgId());
        assertEquals(2, last.getSeq());
        assertEquals(NOW.toEpochMilli(), last.getCreatedAtMillis());
    }

    @Test
    void idempotentReturnsExistingSeq() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("CM")).thenReturn("CM1");

        SendMessageResult first = useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "dup", 1L));
        verify(messageRepository).save(messageCaptor.capture());
        ChatMessage saved = messageCaptor.getValue();

        when(messageRepository.findBySessionAndClientMsgId(anyLong(), anyString())).thenReturn(Optional.of(saved));
        SendMessageResult second = useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "dup", 1L));

        assertEquals(1, first.seq());
        assertEquals("CM1", first.messageId());
        assertEquals(1, second.seq());
        assertEquals("CM1", second.messageId());
        verify(messageRepository, times(1)).save(any());
    }

    @Test
    void closedSessionRejected() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.CLOSED, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        BizException e = assertThrows(BizException.class,
                () -> useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "c1", 1L)));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void notFoundThrows() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "c1", 1L)));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void textMessageDefaultsToTextType() {
        // 旧调用只传 5 个参数（无 msgType）：必须落 TEXT，不能变成 null 让读侧解析炸掉
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionAndClientMsgId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(idGen.next("CM")).thenReturn("CM1");
        when(clock.instant()).thenReturn(NOW);

        useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "c1", 1L));

        verify(messageRepository).save(messageCaptor.capture());
        assertEquals(ChatMsgType.TEXT, messageCaptor.getValue().getMsgType());
    }

    @Test
    void imageMessageKeepsImageTypeAndFileIdAsContent() {
        // 图片消息：content 存 fileId（不塞 base64），msg_type 落 IMAGE
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 9L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionAndClientMsgId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(idGen.next("CM")).thenReturn("CM2");
        when(clock.instant()).thenReturn(NOW);

        useCase.send(new SendMessageCmd(1L, SenderType.STAFF, "2026/11/ab/shot.png", "c-img", 9L, "IMAGE"));

        verify(messageRepository).save(messageCaptor.capture());
        ChatMessage saved = messageCaptor.getValue();
        assertEquals(ChatMsgType.IMAGE, saved.getMsgType());
        assertEquals("2026/11/ab/shot.png", saved.getContent());
        assertEquals(SenderType.STAFF, saved.getSenderType());
    }

    /**
     * 订单卡片消息（2026-11-15 用户要求：客服咨询里可以把订单发给客服）。
     * <p>content 是订单摘要 JSON（订单号/状态/金额/首个商品名），双方都按 msgType=ORDER 渲染成卡片；
     * 若枚举漏了 ORDER，{@code ChatMsgType.of} 会静默降级成 TEXT，客服端只能看到一坨 JSON 文本。</p>
     */
    @Test
    void orderCardMessageKeepsOrderType() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionAndClientMsgId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(idGen.next("CM")).thenReturn("CM3");
        when(clock.instant()).thenReturn(NOW);

        String card = "{\"orderNo\":\"SO20261115000001\",\"status\":\"COMPLETED\",\"payableCent\":128800}";
        useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, card, "c-order", 5L, "ORDER"));

        verify(messageRepository).save(messageCaptor.capture());
        ChatMessage saved = messageCaptor.getValue();
        assertEquals(ChatMsgType.ORDER, saved.getMsgType(), "订单卡片必须落 ORDER，不能降级成 TEXT");
        assertEquals(card, saved.getContent());
    }

    @Test
    void unknownMsgTypeFallsBackToText() {
        // 脏数据 / 旧客户端传了没见过的类型：按 TEXT 处理，不让一条坏记录把历史接口打挂
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionAndClientMsgId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(idGen.next("CM")).thenReturn("CM4");
        when(clock.instant()).thenReturn(NOW);

        useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "c5", 5L, "VIDEO"));

        verify(messageRepository).save(messageCaptor.capture());
        assertEquals(ChatMsgType.TEXT, messageCaptor.getValue().getMsgType());
    }
}
