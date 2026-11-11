package com.sent.sports.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.chat.mapper.po.ChatSessionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服会话 Mapper（P5-18）。
 * <p>待接入队列的排序口径固化为 {@code vip DESC, priority DESC, created_at ASC}：
 * 会员优先，其次人工调序，同档先到先接——排序下沉到 SQL 并走 {@code idx_session_queue}，
 * 避免把全量会话拉回内存再排。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionPO> {

    /** 待接入队列（坐席工作台轮询）。 */
    @Select("SELECT * FROM chat_session WHERE status = 'WAITING' "
            + "ORDER BY vip DESC, priority DESC, created_at ASC LIMIT #{limit}")
    List<ChatSessionPO> selectWaitingQueue(@Param("limit") int limit);

    /** 某客户进行中/待接入的会话（复用会话、限制并发）。 */
    @Select("SELECT * FROM chat_session WHERE customer_id = #{customerId} "
            + "AND status IN ('WAITING', 'ACTIVE') ORDER BY id DESC")
    List<ChatSessionPO> selectOpenByCustomer(@Param("customerId") long customerId);

    /** 按状态查询（升序，便于稳定断言与分页）。 */
    @Select("SELECT * FROM chat_session WHERE status = #{status} ORDER BY id")
    List<ChatSessionPO> selectByStatus(@Param("status") String status);

    /** 某客户的全部会话（不限状态，用于历史与并发校验）。 */
    @Select("SELECT * FROM chat_session WHERE customer_id = #{customerId} ORDER BY id DESC")
    List<ChatSessionPO> selectByCustomerId(@Param("customerId") long customerId);

    /** 某坐席进行中的会话（掉线回收、并发上限判断）。 */
    @Select("SELECT * FROM chat_session WHERE staff_id = #{staffId} AND status = 'ACTIVE' ORDER BY id")
    List<ChatSessionPO> selectActiveByStaff(@Param("staffId") long staffId);

    /** 全站进行中会话数（并发上限）。 */
    @Select("SELECT COUNT(*) FROM chat_session WHERE status = 'ACTIVE'")
    int countActive();

    /**
     * 原子分配消息序号：返回自增后的值。
     * 先 {@code UPDATE}（行锁内完成 +1），再读回，保证并发下序号不重。
     */
    @Update("UPDATE chat_session SET seq = seq + 1 WHERE id = #{sessionId}")
    int increaseSeq(@Param("sessionId") long sessionId);

    /** 读回当前序号（紧跟 {@link #increaseSeq} 使用，同一事务内行锁未释放）。 */
    @Select("SELECT seq FROM chat_session WHERE id = #{sessionId}")
    Long selectSeq(@Param("sessionId") long sessionId);

    /** 接入坐席：仅 WAITING 可接，返回 0 表示已被他人抢先接入。 */
    @Update("UPDATE chat_session SET status = 'ACTIVE', staff_id = #{staffId}, started_at = NOW(3) "
            + "WHERE id = #{sessionId} AND status = 'WAITING'")
    int assignStaff(@Param("sessionId") long sessionId, @Param("staffId") long staffId);

    /** 结束会话：仅 ACTIVE 可结束。 */
    @Update("UPDATE chat_session SET status = 'CLOSED', ended_at = NOW(3), close_reason = #{reason} "
            + "WHERE id = #{sessionId} AND status = 'ACTIVE'")
    int closeSession(@Param("sessionId") long sessionId, @Param("reason") String reason);

    /** 刷新最后消息时间（会话超时判定依据）。 */
    @Update("UPDATE chat_session SET last_msg_at = NOW(3) WHERE id = #{sessionId}")
    int touchLastMsg(@Param("sessionId") long sessionId);

    /** 长时间无消息的进行中会话（P5-45 超时结束）。 */
    @Select("SELECT * FROM chat_session WHERE status = 'ACTIVE' "
            + "AND (last_msg_at IS NULL OR last_msg_at <= #{before}) "
            + "ORDER BY last_msg_at LIMIT #{limit}")
    List<ChatSessionPO> selectIdleSessions(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
