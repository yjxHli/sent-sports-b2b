package com.sent.sports.chat.service.dto;

import java.util.List;

/**
 * 工作台待处理会话（左侧「进行中 / 排队中」两栏）：跨模块可用的中立出参。
 *
 * @param active  当前客服进行中的会话（按会话号升序）
 * @param waiting 排队中的会话（按创建时间先来后到，position 即其排队位置，1 开始）
 */
public record OpenSessionsResult(List<Row> active, List<Row> waiting) {

    /**
     * 会话行。
     *
     * @param queuePosition        排队位置；进行中为 0
     * @param customerName         进线客户昵称（未设置昵称为 null，前端兜底成「客户 {id}」）
     * @param customerAvatarFileId 进线客户头像 fileId（无头像为 null，前端用姓名首字兜底）
     */
    public record Row(long sessionId, String status, Long staffId, Long customerId,
                      long waitingSinceMillis, int queuePosition,
                      String customerName, String customerAvatarFileId) {

        /** 兼容旧构造：不需要客户展示信息时（排队行不再展示头像）调用。 */
        public Row(long sessionId, String status, Long staffId, Long customerId,
                   long waitingSinceMillis, int queuePosition) {
            this(sessionId, status, staffId, customerId, waitingSinceMillis, queuePosition, null, null);
        }
    }
}
