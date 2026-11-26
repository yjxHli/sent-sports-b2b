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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        assertEquals("SUBMITTED", saved.getValue().getSubmitStatus());
        assertNotNull(saved.getValue().getSubmittedAt(), "提交必须有提交时间");
        verify(noteMapper, never()).updateBySessionId(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), any(), anyString(), anyString(), anyString(), any());
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
        verify(noteMapper).updateBySessionId(eq(7L), eq(9001L), eq("补充：客户要加急"), eq("QUALITY"),
                eq(null), eq(null), eq(null), eq("NO_REPLY"), eq("UNRESOLVED"), eq("已升级给主管跟进"),
                eq(false), eq(null), eq(null), eq(null), eq("SUBMITTED"), any());
    }

    /**
     * 暂时保存（2026-11-26 用户要求）：允许必填项为空，落库为 DRAFT。
     * 客服可以保存后去接待下一位客户，空闲时回来继续填。
     */
    @Test
    void draftSaveAllowsIncompleteTicket() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L)));
        SaveNoteCmd draft = new SaveNoteCmd(7L, 9001L, "  ", "INQUIRY", "CLEAR", "DONE", "",
                null, null, null, false, null, null, false);

        ChatSessionNote note = useCase.save(draft);

        assertEquals(ChatSessionNote.SubmitStatus.DRAFT, note.getSubmitStatus());
        ArgumentCaptor<ChatSessionNotePO> saved = ArgumentCaptor.forClass(ChatSessionNotePO.class);
        verify(noteMapper).insert(saved.capture());
        assertEquals("DRAFT", saved.getValue().getSubmitStatus());
        assertNull(saved.getValue().getSubmittedAt(), "草稿没有提交时间");
        verify(noteMapper, never()).updateBySessionId(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), any(), anyString(), anyString(), anyString(), any());
    }

    /** 再点「提交」时必须重新校验必填：草稿能存，但提交不允许半成品。 */
    @Test
    void submitRejectsDraftWithoutRequiredFields() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L)));
        SaveNoteCmd submit = new SaveNoteCmd(7L, 9001L, "概述", "INQUIRY", "CLEAR", "DONE", "  ",
                null, null, null, false, null, null, true);

        BizException e = assertThrows(BizException.class, () -> useCase.save(submit));

        assertEquals(40005, e.getErrorCode().getCode());
        verify(noteMapper, never()).insert(any(ChatSessionNotePO.class));
        verify(noteMapper, never()).updateBySessionId(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), any(), anyString(), anyString(), anyString(), any());
    }

    /** 提交已存在的草稿：同一会话原位更新为 SUBMITTED，审查列表据此出现。 */
    @Test
    void submitDraftUpdatesSameRowToSubmitted() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.CLOSED, 9001L)));
        ChatSessionNotePO existing = new ChatSessionNotePO();
        existing.setSessionId(7L);
        existing.setSubmitStatus("DRAFT");
        when(noteMapper.selectBySessionId(7L)).thenReturn(existing);

        useCase.save(cmd(9001L));

        verify(noteMapper).updateBySessionId(eq(7L), eq(9001L), eq("咨询定制足球印字"), eq("INQUIRY"),
                eq(null), eq(null), eq(null), eq("CLEAR"), eq("DONE"), eq("已告知起订量与交期"),
                eq(false), eq(null), eq(null), eq(null), eq("SUBMITTED"), any());
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

    @Test
    void extendedTicketFieldsPersisted() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L)));
        SaveNoteCmd cmd = new SaveNoteCmd(7L, 9001L, "咨询定制足球印字", "商品咨询", "明确信息", "已完成",
                "已告知起订量与交期", "印字Logo", "本周内到货", "订单 S001 / SKU F001",
                true, "2026-11-26 10:30", "13900001111", "[\"file-1\"]", true);

        ChatSessionNote saved = useCase.save(cmd);

        ArgumentCaptor<ChatSessionNotePO> captured = ArgumentCaptor.forClass(ChatSessionNotePO.class);
        verify(noteMapper).insert(captured.capture());
        assertEquals("INQUIRY", captured.getValue().getCategory(), "中文分类应归一成稳定码");
        assertEquals("印字Logo", captured.getValue().getSubCategory());
        assertEquals("本周内到货", captured.getValue().getCustomerDemand());
        assertEquals("订单 S001 / SKU F001", captured.getValue().getVerifiedInfo());
        assertEquals(true, captured.getValue().getNeedFollowup());
        assertEquals("13900001111", captured.getValue().getFollowupContact(), "回访方式由客服手填，不再默认取客户手机号");
        assertEquals("[\"file-1\"]", captured.getValue().getAttachmentsJson());
        assertEquals("SUBMITTED", captured.getValue().getSubmitStatus());
        assertEquals(true, saved.isNeedFollowup());
    }

    @Test
    void blankSummaryRejectedWithBusinessCode40005() {
        // 2026-11-25 服务工单：必填项缺失不能再报 40001 参数错误，
        // 必须用独立业务码让前端明确提示「服务工单未填写完整」。
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L)));

        BizException e = assertThrows(BizException.class, () -> useCase.save(
                new SaveNoteCmd(7L, 9001L, "   ", "INQUIRY", "CLEAR", "DONE", "办法")));

        assertEquals(40005, e.getErrorCode().getCode());
        verify(noteMapper, never()).insert(any(ChatSessionNotePO.class));
    }

    @Test
    void blankSolutionRejectedWithBusinessCode40005() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(
                new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L)));

        BizException e = assertThrows(BizException.class, () -> useCase.save(
                new SaveNoteCmd(7L, 9001L, "概述", "INQUIRY", "CLEAR", "DONE", "  ")));

        assertEquals(40005, e.getErrorCode().getCode());
        verify(noteMapper, never()).insert(any(ChatSessionNotePO.class));
    }
}
