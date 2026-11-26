package com.sent.sports.chat.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 服务工单领域校验：问题描述、分类、明确信息、处理结果、解决办法五项必填，
 * 且分类 / 信息状态 / 处理结果只能取约定枚举。规则放在领域层，REST 与任何后续入口共用同一口径。
 * 2026-11-25：失败统一用 40005「服务工单未填写完整」。
 */
class ChatSessionNoteTest {

    private static ChatSessionNote note(String summary, String category, String info,
                                        String result, String solution) {
        return new ChatSessionNote(7L, 9002L, 9001L, summary, category, info, result, solution);
    }

    @Test
    void acceptsAllDocumentedEnums() {
        ChatSessionNote n = note("咨询定制足球印字", "INQUIRY", "CLEAR", "DONE", "已告知起订量与交期");

        assertEquals(ChatSessionNote.Category.INQUIRY, n.getCategory());
        assertEquals(ChatSessionNote.Info.CLEAR, n.getInfo());
        assertEquals(ChatSessionNote.Result.DONE, n.getResult());
        assertEquals("咨询定制足球印字", n.getSummary());
    }

    @Test
    void blankSummaryRejected() {
        assertInvalid(() -> note("  ", "INQUIRY", "CLEAR", "DONE", "办法"));
    }

    @Test
    void blankSolutionRejected() {
        assertInvalid(() -> note("概述", "INQUIRY", "CLEAR", "DONE", null));
    }

    @Test
    void illegalCategoryRejected() {
        assertInvalid(() -> note("概述", "UNKNOWN", "CLEAR", "DONE", "办法"));
    }

    @Test
    void illegalInfoRejected() {
        assertInvalid(() -> note("概述", "INQUIRY", "REPLIED", "DONE", "办法"));
    }

    @Test
    void illegalResultRejected() {
        assertInvalid(() -> note("概述", "INQUIRY", "CLEAR", "SOLVED", "办法"));
    }

    /**
     * 草稿（2026-11-26 用户要求）：客服可以先「暂时保存」再去接待别的客户，
     * 草稿允许必填项为空，但不能存进非法枚举——否则下拉一改历史草稿就再也提交不了。
     */
    @Test
    void draftAllowsBlankRequiredFields() {
        ChatSessionNote draft = ChatSessionNote.draft(7L, 9002L, 9001L, "  ", "INQUIRY",
                null, null, null, "CLEAR", "DONE", null, false, null, null, null);

        assertEquals(ChatSessionNote.SubmitStatus.DRAFT, draft.getSubmitStatus());
        assertNull(draft.getSummary());
        assertNull(draft.getSolution());
        assertEquals(ChatSessionNote.Category.INQUIRY, draft.getCategory());
    }

    @Test
    void draftKeepsSubmittedStatusForFullNote() {
        ChatSessionNote submitted = note("概述", "INQUIRY", "CLEAR", "DONE", "办法");

        assertEquals(ChatSessionNote.SubmitStatus.SUBMITTED, submitted.getSubmitStatus());
    }

    @Test
    void draftStillRejectsIllegalEnum() {
        assertInvalid(() -> ChatSessionNote.draft(7L, 9002L, 9001L, "概述", "NOT_A_CATEGORY",
                null, null, null, "CLEAR", "DONE", "办法", false, null, null, null));
    }

    /** 提交时勾了「需回访」却没有联系方式 → 40005；草稿允许先空着。 */
    @Test
    void submitRequiresFollowupContact() {
        assertInvalid(() -> new ChatSessionNote(7L, 9002L, 9001L, "概述", "INQUIRY",
                null, null, null, "CLEAR", "DONE", "办法", true, null, null, null));

        ChatSessionNote draft = ChatSessionNote.draft(7L, 9002L, 9001L, "概述", "INQUIRY",
                null, null, null, "CLEAR", "DONE", "办法", true, null, null, null);
        assertEquals(null, draft.getFollowupContact());
    }

    /** 前端下拉给中文文案时归一成稳定码落库，不能把「商品咨询」原样写进 category。 */
    @Test
    void chineseLabelNormalizedToEnum() {
        ChatSessionNote n = note("概述", "商品咨询", "明确信息", "已完成", "办法");

        assertEquals(ChatSessionNote.Category.INQUIRY, n.getCategory());
        assertEquals(ChatSessionNote.Info.CLEAR, n.getInfo());
        assertEquals(ChatSessionNote.Result.DONE, n.getResult());
    }

    private static void assertInvalid(Runnable action) {
        BizException e = assertThrows(BizException.class, action::run);
        assertEquals(ErrorCode.SERVICE_TICKET_INCOMPLETE, e.getErrorCode());
    }
}
