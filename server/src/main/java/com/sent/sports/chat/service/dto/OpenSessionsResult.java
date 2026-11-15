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
     * @param queuePosition 排队位置；进行中为 0
     */
    public record Row(long sessionId, String status, Long staffId, Long customerId,
                      long waitingSinceMillis, int queuePosition) {
    }
}
