package com.sent.sports.chat.mapper;

import com.sent.sports.chat.mapper.po.StaffPresencePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 坐席在线状态 Mapper（{@code staff_presence}，2026-11-28 批次 2 / P1-08）。
 *
 * <p>表在 V9 就建好了，但一直只有读（指标页 {@code LEFT JOIN}）和一条空转的回收 {@code UPDATE}，
 * 真实状态存在进程内 Map 里——指标页因此恒显示「离线」。这里补上写面。</p>
 *
 * <p><b>心跳口径</b>：每次 {@link #upsert} 都把 {@code last_heartbeat_at} 刷成数据库当前时间。
 * 心跳必须由「写」驱动（切状态、接单、关单都会写），不能用「读」顺带刷新——
 * 否则一个纯查询就能把掉线坐席续命，回收任务永远抓不到僵尸坐席。</p>
 */
@Mapper
public interface StaffPresenceMapper {

    /** 以 staff_id 为主键 upsert：不存在则建行，存在则覆盖状态/上限/负载并刷新心跳。 */
    @Insert("INSERT INTO staff_presence (staff_id, status, max_sessions, active_sessions, last_heartbeat_at) "
            + "VALUES (#{staffId}, #{status}, #{maxSessions}, #{activeSessions}, NOW(3)) "
            + "ON DUPLICATE KEY UPDATE status = #{status}, max_sessions = #{maxSessions}, "
            + "active_sessions = #{activeSessions}, last_heartbeat_at = NOW(3)")
    int upsert(@Param("staffId") long staffId,
               @Param("status") String status,
               @Param("maxSessions") int maxSessions,
               @Param("activeSessions") int activeSessions);

    @Select("SELECT staff_id, status, max_sessions, active_sessions, last_heartbeat_at "
            + "FROM staff_presence WHERE staff_id = #{staffId}")
    StaffPresencePO selectByStaffId(@Param("staffId") long staffId);

    @Select("SELECT staff_id, status, max_sessions, active_sessions, last_heartbeat_at FROM staff_presence")
    List<StaffPresencePO> selectAll();
}
