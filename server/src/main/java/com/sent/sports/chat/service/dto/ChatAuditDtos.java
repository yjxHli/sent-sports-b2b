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

    public record NoteVO(long sessionId, String summary, String category, String info, String result,
                         String solution, long staffId, long updatedAtMillis) {
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
