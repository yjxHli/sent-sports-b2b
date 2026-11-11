package com.sent.sports.chat.service.dto;

import java.util.List;

/**
 * 超时自动关闭结果（中立出参）：关闭数量、被关闭的会话号列表。
 */
public record CloseExpiredResult(int closedCount, List<Long> closedSessionIds) {
}
