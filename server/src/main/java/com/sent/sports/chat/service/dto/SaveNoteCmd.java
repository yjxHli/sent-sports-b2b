package com.sent.sports.chat.service.dto;

/**
 * 客服填写服务工单的命令；staffId 必须是该会话的接待人。
 *
 * <p>{@code submit} 区分「暂时保存」与「提交工单」（2026-11-26 用户要求）：
 * 暂存允许必填项为空且不进客服审查；提交才校验完整并出现在审查列表。
 * 保留 7 参 / 13 参构造器兼容旧调用方，默认按「提交」处理。</p>
 */
public record SaveNoteCmd(long sessionId, long staffId, String summary, String category,
                          String info, String result, String solution,
                          String subCategory, String customerDemand, String verifiedInfo,
                          boolean needFollowup, String followupTime, String followupContact,
                          String attachmentsJson, boolean submit) {

    public SaveNoteCmd(long sessionId, long staffId, String summary, String category,
                       String info, String result, String solution) {
        this(sessionId, staffId, summary, category, info, result, solution,
                null, null, null, false, null, null, null, true);
    }

    public SaveNoteCmd(long sessionId, long staffId, String summary, String category,
                       String info, String result, String solution,
                       String subCategory, String customerDemand, String verifiedInfo,
                       boolean needFollowup, String followupTime, String attachmentsJson) {
        this(sessionId, staffId, summary, category, info, result, solution,
                subCategory, customerDemand, verifiedInfo, needFollowup, followupTime, null,
                attachmentsJson, true);
    }

    /** 无回访联系方式的暂存/提交入口（前端未升级时的兼容形态）。 */
    public SaveNoteCmd(long sessionId, long staffId, String summary, String category,
                       String info, String result, String solution,
                       String subCategory, String customerDemand, String verifiedInfo,
                       boolean needFollowup, String followupTime, String attachmentsJson,
                       boolean submit) {
        this(sessionId, staffId, summary, category, info, result, solution,
                subCategory, customerDemand, verifiedInfo, needFollowup, followupTime, null,
                attachmentsJson, submit);
    }
}
