package com.sent.sports.chat.service.dto;

/** 客服填写会话小结的命令；staffId 必须是该会话的接待人。 */
public record SaveNoteCmd(long sessionId, long staffId, String summary, String category,
                          String info, String result, String solution) {
}
