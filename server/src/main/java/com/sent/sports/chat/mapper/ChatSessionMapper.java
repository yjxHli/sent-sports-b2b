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

    /**
     * 排队队列（严格先来后到）：工作台的排队位置与客户端的排队位置必须同一口径，
     * 因此展示用 FIFO 排序（{@code created_at, id}），与 {@link #countWaitingBefore} 一致。
     * <p>VIP 优先只作用于「人工拉取下一个」（{@link #selectWaitingQueue}），不影响排队位置的展示。
     */
    @Select("SELECT * FROM chat_session WHERE status = 'WAITING' "
            + "ORDER BY created_at ASC, id ASC LIMIT #{limit}")
    List<ChatSessionPO> selectWaitingFifo(@Param("limit") int limit);

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

    /** 某坐席进行中会话数（负载真源，上线时校准用）。 */
    @Select("SELECT COUNT(*) FROM chat_session WHERE staff_id = #{staffId} AND status = 'ACTIVE'")
    int countActiveByStaff(@Param("staffId") long staffId);

    /**
     * 某坐席在 [from, to) 区间内结束的会话数（工作台「今日完成接待数」）。
     * <p>用 {@code ended_at} 半开区间判定，跨零点自动换日，不需要定时任务清零；
     * 只统计 CLOSED，避免把超时关闭之外的中间态算进接待量。</p>
     */
    @Select("SELECT COUNT(*) FROM chat_session WHERE staff_id = #{staffId} AND status = 'CLOSED' "
            + "AND ended_at IS NOT NULL AND ended_at >= #{from} AND ended_at < #{to}")
    int countClosedByStaffBetween(@Param("staffId") long staffId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    /**
     * 排在指定会话之前的排队会话数（排队位置 = 该值 + 1）。
     * <p>按 {@code created_at} 升序判定先来后到，同一毫秒用主键兜底，避免并列时位置抖动。
     */
    @Select("SELECT COUNT(*) FROM chat_session WHERE status = 'WAITING' "
            + "AND (created_at < #{createdAt} OR (created_at = #{createdAt} AND id < #{sessionId}))")
    int countWaitingBefore(@Param("sessionId") long sessionId,
                           @Param("createdAt") LocalDateTime createdAt);

    /**
     * 原子分配消息序号：返回自增后的值。
     * 先 {@code UPDATE}（行锁内完成 +1），再读回，保证并发下序号不重。
     */
    @Update("UPDATE chat_session SET seq = seq + 1 WHERE id = #{sessionId}")
    int increaseSeq(@Param("sessionId") long sessionId);

    /** 读回当前序号（紧跟 {@link #increaseSeq} 使用，同一事务内行锁未释放）。 */
    @Select("SELECT seq FROM chat_session WHERE id = #{sessionId}")
    Long selectSeq(@Param("sessionId") long sessionId);

    /**
     * 清空接待坐席（会话释放回排队）。
     * <p>{@code updateById} 默认跳过 null 字段，释放后 {@code staff_id} 会残留旧坐席，
     * 工作台就会显示「待接入 + 旧客服」，客户消息也可能被误投给已离线的坐席，故单独显式清空。
     */
    @Update("UPDATE chat_session SET staff_id = NULL WHERE id = #{sessionId}")
    int clearStaffId(@Param("sessionId") long sessionId);

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

    /**
     * 结束打点：只在首次结束时写 {@code ended_at}，重复关闭不覆盖原时间。
     * <p>工作台「今日完成接待数」按 ended_at 统计，缺这个字段就等于接待量永远是 0。
     */
    @Update("UPDATE chat_session SET ended_at = NOW(3) WHERE id = #{sessionId} AND ended_at IS NULL")
    int markEnded(@Param("sessionId") long sessionId);

    /** 长时间无消息的进行中会话（P5-45 超时结束）。 */
    @Select("SELECT * FROM chat_session WHERE status = 'ACTIVE' "
            + "AND (last_msg_at IS NULL OR last_msg_at <= #{before}) "
            + "ORDER BY last_msg_at LIMIT #{limit}")
    List<ChatSessionPO> selectIdleSessions(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
