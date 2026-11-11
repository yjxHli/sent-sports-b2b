package com.sent.sports.chat.repository;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;

import java.util.List;
import java.util.Optional;

/**
 * 会话仓储端口（plan §7.8）：实现见 Phase 5（MyBatis/Redis）。
 * 仅声明用例层所需的查询与持久化能力，保持 chat 模块内聚。
 */
public interface ChatSessionRepository {

    /** 生成会话主键（long 型，由实现层保证唯一）。 */
    long nextId();

    /** 持久化会话（新增或更新）。 */
    void save(ChatSession session);

    /** 按主键加载（无记录返回 empty）。 */
    Optional<ChatSession> findById(long id);

    /** 某客户的全部会话（用于复用 / 并发校验）。 */
    List<ChatSession> findByCustomerId(long customerId);

    /** 全部待接入（WAITING）会话。 */
    List<ChatSession> findWaiting();

    /** 按状态查询。 */
    List<ChatSession> findByStatus(ChatSessionStatus status);

    /** 某客服当前进行中（ACTIVE）的会话（用于离线释放）。 */
    List<ChatSession> findActiveByStaff(long staffId);

    /** 当前进行中会话总数（用于并发上限判断）。 */
    int countActive();
}
