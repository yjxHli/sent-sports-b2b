package com.sent.sports.chat.service;

import com.sent.sports.chat.dto.ChatDtos;

import java.util.List;

/**
 * 客服会话查询门面（P4-29 接口契约，跨模块可用）。
 * <p>会话/消息实体与仓储尚未建设与读面（P5 DB 范畴），当前由
 * {@code ChatQueryServiceImpl} 桩实现占位；Phase 5 接入实体、在线状态与数据范围（@DataScope）后替换。
 * 契约（接口签名）保持稳定。</p>
 */
public interface ChatQueryService {

    /**
     * 历史消息补拉（不限归属，仅供管理端审查/工作台内部复用）。
     * <p>默认实现等价于「管理端视角」，HTTP 入口禁止直接调用——用户端必须走带
     * {@code viewerCustomerId} 的重载，否则就是 P0-04 的越权读。
     */
    default List<ChatDtos.MessageVO> history(long sessionId, int afterSeq) {
        return history(sessionId, afterSeq, null);
    }

    /**
     * 历史消息补拉（带归属校验，2026-11-28 批次 1 / P0-04）。
     *
     * @param viewerCustomerId 查看人客户号；{@code null} 表示员工/管理端（客服工作台是共享队列，
     *                         排队会话尚未分配坐席，坐席必须先能看历史才能接待）。
     *                         非空且与会话归属不一致抛 {@code FORBIDDEN(40301)}。
     */
    List<ChatDtos.MessageVO> history(long sessionId, int afterSeq, Long viewerCustomerId);

    /** 已读上报：记录该会话已读到的消息序号。 */
    void reportRead(long sessionId, int seq);

    /**
     * 排队状态：会话当前状态 + 排队位置（1 开始，已接入为 0）。
     * <p>位置口径：排在它前面的 WAITING 会话数 + 1（按创建时间先来后到）。
     */
    ChatDtos.QueueVO queue(long sessionId);

    /**
     * 用户进入聊天页时查询尚未结束的会话（只查不建）。
     * <p>发消息才算咨询：首条消息到达前不产生任何会话，客服工作台也不会看到空会话。</p>
     *
     * @return 没有进行中会话时返回 null
     */
    ChatDtos.SessionVO currentOpen(long customerId);

    /**
     * 某客户的历史咨询会话（按仓储返回顺序，最新在前）。
     * <p>用于顶部「历史咨询记录」与右上角「服务进度 / 历史服务进度」；
     * 只给摘要，点进某个会话后再用 {@link #history(long, int)} 拉消息，避免一次拉全量正文。</p>
     *
     * @param limit 最多返回条数，服务端会收敛到 1~50
     */
    List<ChatDtos.SessionSummaryVO> listSessions(long customerId, int limit);
}
