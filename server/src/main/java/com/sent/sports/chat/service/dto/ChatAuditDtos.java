package com.sent.sports.chat.service.dto;

import java.util.List;

/** 客服审查 / 历史服务 / 回放的读模型（前后端字段一一对应）。 */
public final class ChatAuditDtos {

    private ChatAuditDtos() {
    }

    public record SessionDetail(long sessionId, String status, Long staffId, Long customerId,
                                long startedAtMillis, long endedAtMillis, String closeReason) {
    }

    public record MessageLine(int seq, String senderType, String msgType, String content, long createdAtMillis) {
    }

    /**
     * 服务工单记录（V61）：既有小结字段 + 工单扩展字段 + 只读客户基础信息。
     * <p>基础信息由服务端联查客户资料带入，客服不需要也不能手填；咨询时间取会话开始时间。</p>
     * <p>V65 增加 {@code followupContact}（客服手填的回访方式）与 {@code submitStatus/submittedAtMillis}：
     * 前端据此判断「工单是否已提交」——未提交不允许关闭会话窗口。</p>
     */
    public record NoteVO(long sessionId, String summary, String category, String info, String result,
                         String solution, long staffId, long updatedAtMillis,
                         String subCategory, String customerDemand, String verifiedInfo,
                         boolean needFollowup, Long followupTimeMillis, String followupContact,
                         String attachmentsJson, String submitStatus, Long submittedAtMillis,
                         String customerNickname, String customerPhone, String customerEmail,
                         long consultedAtMillis) {

        /** 兼容只关心旧小结字段的调用方（单测 / 老前端）。 */
        public NoteVO(long sessionId, String summary, String category, String info, String result,
                      String solution, long staffId, long updatedAtMillis) {
            this(sessionId, summary, category, info, result, solution, staffId, updatedAtMillis,
                    null, null, null, false, null, null, null, null, null, null, null, null, 0L);
        }

        /** 兼容 V61 形态（无回访方式与提交状态）：按「已提交」处理，避免旧调用方看到半成品态。 */
        public NoteVO(long sessionId, String summary, String category, String info, String result,
                      String solution, long staffId, long updatedAtMillis,
                      String subCategory, String customerDemand, String verifiedInfo,
                      boolean needFollowup, Long followupTimeMillis, String attachmentsJson,
                      String customerNickname, String customerPhone, String customerEmail,
                      long consultedAtMillis) {
            this(sessionId, summary, category, info, result, solution, staffId, updatedAtMillis,
                    subCategory, customerDemand, verifiedInfo, needFollowup, followupTimeMillis,
                    null, attachmentsJson, "SUBMITTED", null,
                    customerNickname, customerPhone, customerEmail, consultedAtMillis);
        }
    }

    public record Transcript(SessionDetail session, List<MessageLine> messages, NoteVO note) {
    }

    /** 历史服务记录：当前客服可回看同一客户最近 10 次已结束会话。 */
    public record HistoryRow(long sessionId, Long staffId, String staffName, long startedAtMillis,
                             long endedAtMillis, String closeReason, String result, int messageCount) {
    }

    /** 客服审查列表行。 */
    public record AuditRow(long sessionId, long customerId, Long staffId, String staffName,
                           long startedAtMillis, long endedAtMillis, String summary,
                           String result, String noteStatus) {
    }

    public record AuditStaffRow(long staffId, String name) {
    }
}
