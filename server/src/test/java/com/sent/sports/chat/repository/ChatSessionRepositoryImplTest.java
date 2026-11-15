package com.sent.sports.chat.repository;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.mapper.ChatSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话仓储单测：释放回队列（staffId=null）必须显式清库。
 * <p>MyBatis-Plus 的 {@code updateById} 默认跳过 null 字段，若只依赖它，
 * 「客服离线释放会话」后 {@code chat_session.staff_id} 会保留旧坐席，
 * 表现为工作台显示「待接入 + 旧客服」，且客户消息被误投给已离线的坐席（客服端收不到）。
 */
@ExtendWith(MockitoExtension.class)
class ChatSessionRepositoryImplTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Test
    void saveWithNullStaffClearsStaffIdExplicitly() {
        when(chatSessionMapper.updateById(any(com.sent.sports.chat.mapper.po.ChatSessionPO.class))).thenReturn(1);
        ChatSessionRepositoryImpl repository = new ChatSessionRepositoryImpl(chatSessionMapper);
        ChatSession released = ChatSession.rehydrate(49L, 9002L, false,
                ChatSessionStatus.WAITING, null, null, 0L, 0L, 3);

        repository.save(released);

        verify(chatSessionMapper).clearStaffId(49L);
    }

    @Test
    void saveWithAssignedStaffDoesNotClear() {        when(chatSessionMapper.updateById(any(com.sent.sports.chat.mapper.po.ChatSessionPO.class))).thenReturn(1);
        ChatSessionRepositoryImpl repository = new ChatSessionRepositoryImpl(chatSessionMapper);
        ChatSession assigned = ChatSession.rehydrate(49L, 9002L, false,
                ChatSessionStatus.ACTIVE, 9001L, null, 0L, 0L, 3);

        repository.save(assigned);

        verify(chatSessionMapper, never()).clearStaffId(any(Long.class));
        ArgumentCaptor<com.sent.sports.chat.mapper.po.ChatSessionPO> captor =
                ArgumentCaptor.forClass(com.sent.sports.chat.mapper.po.ChatSessionPO.class);
        verify(chatSessionMapper).updateById(captor.capture());
        assertEquals(9001L, captor.getValue().getStaffId());
        assertNull(captor.getValue().getCloseReason());
    }

    /**
     * 结束会话必须落 {@code ended_at}。
     * <p>历史缺陷：{@code toPO} 从不写 ended_at（域对象也没有这个字段），
     * 于是「今日完成接待数」按 ended_at 统计时永远是 0——工作台的接待量看着像坏了。
     * 修复：save() 在状态为 CLOSED 时显式补一次打点。
     */
    @Test
    void closingSessionStampsEndedAt() {
        when(chatSessionMapper.updateById(any(com.sent.sports.chat.mapper.po.ChatSessionPO.class))).thenReturn(1);
        ChatSessionRepositoryImpl repository = new ChatSessionRepositoryImpl(chatSessionMapper);
        ChatSession closed = ChatSession.rehydrate(49L, 9002L, false,
                ChatSessionStatus.CLOSED, 9001L, "DONE", 0L, 0L, 3);

        repository.save(closed);

        verify(chatSessionMapper).markEnded(49L);
    }

    /** 进行中的会话不能被误盖结束时间，否则「今日接待」会把在聊的会话也算进去。 */
    @Test
    void activeSessionIsNotStampedAsEnded() {
        when(chatSessionMapper.updateById(any(com.sent.sports.chat.mapper.po.ChatSessionPO.class))).thenReturn(1);
        ChatSessionRepositoryImpl repository = new ChatSessionRepositoryImpl(chatSessionMapper);
        ChatSession active = ChatSession.rehydrate(49L, 9002L, false,
                ChatSessionStatus.ACTIVE, 9001L, null, 0L, 0L, 3);

        repository.save(active);

        verify(chatSessionMapper, never()).markEnded(any(Long.class));
    }
}
