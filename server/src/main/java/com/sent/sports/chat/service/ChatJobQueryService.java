package com.sent.sports.chat.service;

import java.time.LocalDateTime;
import java.util.List;

/** 定时任务专用的会话只读查询口：仅返回会话 ID，PO 留在 chat 模块内。 */
public interface ChatJobQueryService {

    /** 最后消息时间早于 before 且仍 ACTIVE 的会话 ID（限量）。 */
    List<Long> findIdleSessionIds(LocalDateTime before, int limit);
}
