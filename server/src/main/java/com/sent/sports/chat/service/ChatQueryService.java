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

    /** 历史消息补拉：返回会话内序号大于 afterSeq 的消息。 */
    List<ChatDtos.MessageVO> history(long sessionId, int afterSeq);

    /** 已读上报：记录该会话已读到的消息序号。 */
    void reportRead(long sessionId, int seq);

    /**
     * 排队状态：会话当前状态 + 排队位置（1 开始，已接入为 0）。
     * <p>位置口径：排在它前面的 WAITING 会话数 + 1（按创建时间先来后到）。
     */
    ChatDtos.QueueVO queue(long sessionId);
}
