package com.sent.sports.chat;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionNote;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.mapper.ChatSessionNoteMapper;
import com.sent.sports.chat.mapper.po.ChatSessionNotePO;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.SaveSessionNoteUseCase;
import com.sent.sports.chat.service.dto.SaveNoteCmd;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.support.BaseUseCaseTest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 客服小结用例：只有本次接待客服可填写；同一会话重复提交走幂等更新，不产生第二条记录。 */
class SaveSessionNoteUseCaseTest extends BaseUseCaseTest {

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatSessionNoteMapper noteMapper;
    @InjectMocks
    private SaveSessionNoteUseCase useCase;

    private static SaveNoteCmd cmd(long staffId) {
        return new SaveNoteCmd(7L, staffId, "咨询定制足球印字", "INQUIRY", "CLEAR", "DONE", "已告知起订量与交期");
    }

    @Test
    void ownerCanSaveNoteOnce() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L)));
        when(noteMapper.selectBySessionId(7L)).thenReturn(null);

        ChatSessionNote note = useCase.save(cmd(9001L));

        assertEquals(7L, note.getSessionId());
        assertEquals(9002L, note.getCustomerId());
        ArgumentCaptor<ChatSessionNotePO> saved = ArgumentCaptor.forClass(ChatSessionNotePO.class);
        verify(noteMapper).insert(saved.capture());
        assertEquals("INQUIRY", saved.getValue().getCategory());
        assertEquals("CLEAR", saved.getValue().getIssueInfo());
        assertEquals("DONE", saved.getValue().getResult());
        verify(noteMapper, never()).updateBySessionId(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void resubmitUpdatesSameSessionInsteadOfInsertingAgain() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.CLOSED, 9001L)));
        ChatSessionNotePO existing = new ChatSessionNotePO();
        existing.setSessionId(7L);
        when(noteMapper.selectBySessionId(7L)).thenReturn(existing);

        useCase.save(new SaveNoteCmd(7L, 9001L, "补充：客户要加急", "QUALITY", "NO_REPLY",
                "UNRESOLVED", "已升级给主管跟进"));

        verify(noteMapper, never()).insert(any(ChatSessionNotePO.class));
        verify(noteMapper).updateBySessionId(7L, 9001L, "补充：客户要加急", "QUALITY",
                "NO_REPLY", "UNRESOLVED", "已升级给主管跟进");
    }

    @Test
    void otherStaffCannotFillNote() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L)));

        BizException e = assertThrows(BizException.class, () -> useCase.save(cmd(9999L)));

        assertEquals(ErrorCode.BIZ_REJECT, e.getErrorCode());
        verify(noteMapper, never()).insert(any(ChatSessionNotePO.class));
    }

    @Test
    void unassignedSessionCannotFillNote() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.WAITING, null)));

        assertThrows(BizException.class, () -> useCase.save(cmd(9001L)));
        verify(noteMapper, never()).insert(any(ChatSessionNotePO.class));
    }

    @Test
    void missingSessionRejected() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> useCase.save(cmd(9001L)));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }

    @Test
    void invalidEnumRejectedBeforeWriting() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L)));

        assertThrows(BizException.class, () -> useCase.save(
                new SaveNoteCmd(7L, 9001L, "概述", "NOT_A_CATEGORY", "CLEAR", "DONE", "办法")));
        verify(noteMapper, never()).insert(any(ChatSessionNotePO.class));
    }
}
