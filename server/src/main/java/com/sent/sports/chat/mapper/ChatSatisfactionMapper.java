package com.sent.sports.chat.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 客服满意度评价 Mapper。
 * <p>唯一索引 {@code uk_satisfaction_session} 是防重的唯一真源：重复打分由调用方
 * 先查后返（幂等），并发撞索引时按「已评价」处理，不引入分布式锁。</p>
 */
@Mapper
public interface ChatSatisfactionMapper {

    @Insert("INSERT INTO chat_satisfaction (session_id, staff_id, customer_id, score, message_id, created_by) "
            + "VALUES (#{sessionId}, #{staffId}, #{customerId}, #{score}, #{messageId}, #{customerId})")
    int insert(@Param("sessionId") long sessionId,
               @Param("staffId") long staffId,
               @Param("customerId") long customerId,
               @Param("score") String score,
               @Param("messageId") String messageId);

    @Select("SELECT score FROM chat_satisfaction WHERE session_id = #{sessionId}")
    String selectScoreBySession(@Param("sessionId") long sessionId);

    @Select("SELECT COUNT(*) FROM chat_satisfaction WHERE staff_id = #{staffId} "
            + "AND created_at >= #{from} AND created_at < #{to}")
    int countRatedBetween(@Param("staffId") long staffId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    @Select("SELECT COUNT(*) FROM chat_satisfaction WHERE staff_id = #{staffId} "
            + "AND score = 'SATISFIED' AND created_at >= #{from} AND created_at < #{to}")
    int countSatisfiedBetween(@Param("staffId") long staffId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);
}
