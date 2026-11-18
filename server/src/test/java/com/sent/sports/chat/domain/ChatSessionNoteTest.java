package com.sent.sports.chat.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 会话小结领域校验（2026-11-18 用户要求）：问题概述、分类、用户问题信息、处理结果、解决办法五项必填，
 * 且分类 / 信息状态 / 处理结果只能取约定枚举。规则放在领域层，REST 与任何后续入口共用同一口径。
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

    /** 中文展示值不是枚举名：必须传 QUALITY/INQUIRY/UNCLEAR 这类稳定码，不能把「商品咨询」当枚举存库。 */
    @Test
    void chineseLabelIsNotAnEnumConstant() {
        assertInvalid(() -> note("概述", "商品咨询", "CLEAR", "DONE", "办法"));
    }

    private static void assertInvalid(Runnable action) {
        BizException e = assertThrows(BizException.class, action::run);
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }
}
