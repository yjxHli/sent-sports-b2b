package com.sent.sports.chat;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatMsgType;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.dto.SatisfactionDtos.RateResult;
import com.sent.sports.chat.mapper.ChatSatisfactionMapper;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.ChatNotifier;
import com.sent.sports.chat.service.RateSatisfactionUseCase;
import com.sent.sports.chat.service.SendSatisfactionCardUseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 客户打分用例（2026-11-17）：幂等、档位校验、会话结束后仍可评价。 */
class RateSatisfactionUseCaseTest extends BaseUnitTest {

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private ChatSatisfactionMapper satisfactionMapper;
    @Mock
    private ChatNotifier notifier;

    @InjectMocks
    private RateSatisfactionUseCase useCase;

    private static ChatSession session(ChatSessionStatus status) {
        return ChatSession.rehydrate(11L, 9002L, false, status, 9001L, null,
                1_700_000_000_000L, 1_700_000_000_000L, 3);
    }

    private void stubOpenSession(ChatSessionStatus status) {
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session(status)));
        when(satisfactionMapper.selectScoreBySession(11L)).thenReturn(null);
    }

    @Test
    void ratesSatisfiedAndUpdatesCardInPlace() {
        stubOpenSession(ChatSessionStatus.ACTIVE);
        ChatMessage card = new ChatMessage("CM-CARD", 11L, SenderType.SYSTEM,
                SendSatisfactionCardUseCase.pendingContent(),
                SendSatisfactionCardUseCase.clientMsgId(11L), 3, 1_700_000_000_000L, ChatMsgType.SATISFY);
        when(messageRepository.findBySessionAndClientMsgId(11L,
                SendSatisfactionCardUseCase.clientMsgId(11L))).thenReturn(Optional.of(card));

        RateResult result = useCase.rate(11L, "SATISFIED", 9002L);

        assertEquals("SATISFIED", result.score());
        assertEquals("CM-CARD", result.messageId());
        verify(satisfactionMapper).insert(11L, 9001L, 9002L, "SATISFIED", "CM-CARD");
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(messageRepository).updateContent(eq("CM-CARD"), content.capture());
        org.junit.jupiter.api.Assertions.assertTrue(content.getValue().contains("RATED"));
        org.junit.jupiter.api.Assertions.assertTrue(content.getValue().contains("SATISFIED"));
        verify(notifier).messagePublished(any(), any());
    }

    @Test
    void repeatedRatingIsIdempotent() {
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session(ChatSessionStatus.CLOSED)));
        when(satisfactionMapper.selectScoreBySession(11L)).thenReturn("NORMAL");

        RateResult result = useCase.rate(11L, "SATISFIED", 9002L);

        assertEquals("NORMAL", result.score(), "同一会话只能有一条有效评价，重复点击回原档位");
        verify(satisfactionMapper, never()).insert(anyLong(), anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void closedSessionStillAcceptsRating() {
        stubOpenSession(ChatSessionStatus.CLOSED);

        RateResult result = useCase.rate(11L, "UNSATISFIED", 9002L);

        assertEquals("UNSATISFIED", result.score(),
                "评价卡是结束会话时发出的，必须允许已结束会话打分");
    }

    @Test
    void invalidScoreRejected() {
        BizException ex = assertThrows(BizException.class, () -> useCase.rate(11L, "VERY_GOOD", 9002L));

        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(satisfactionMapper, never()).insert(anyLong(), anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("P0-补漏 客户 A 给客户 B 的会话打分 → FORBIDDEN，且无任何副作用")
    void otherCustomerCannotRate() {
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session(ChatSessionStatus.CLOSED)));

        BizException ex = assertThrows(BizException.class, () -> useCase.rate(11L, "SATISFIED", 9003L));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(satisfactionMapper, never()).insert(anyLong(), anyLong(), anyLong(), anyString(), any());
        verify(messageRepository, never()).updateContent(anyString(), anyString());
        verify(notifier, never()).messagePublished(any(), any());
    }

    @Test
    @DisplayName("内部/员工口径（viewer=null）仍可为任意会话打分，保持系统任务与客服兜底")
    void internalViewerMayRate() {
        stubOpenSession(ChatSessionStatus.ACTIVE);

        RateResult result = useCase.rate(11L, "SATISFIED", null);

        assertEquals("SATISFIED", result.score());
        verify(satisfactionMapper).insert(11L, 9001L, 9002L, "SATISFIED", null);
    }
}
