package com.sent.sports.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.chat.mapper.po.ChatMessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客服消息 Mapper（P5-18）：历史查询一律按 {@code seq} 升序，与前端渲染顺序一致；
 * 断线重连用「增量补拉」（{@link #selectSince}）而不是重新拉全量。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessagePO> {

    /** 幂等去重：同一会话同一客户端消息号只落一条。 */
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} AND client_msg_id = #{clientMsgId}")
    ChatMessagePO selectByClientMsgId(@Param("sessionId") long sessionId, @Param("clientMsgId") String clientMsgId);

    /** 某会话全部历史（seq 升序）。 */
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY seq")
    List<ChatMessagePO> selectBySessionId(@Param("sessionId") long sessionId);

    /** 增量补拉：取 seq 大于游标的消息（重连后只补缺口）。 */
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} AND seq > #{afterSeq} "
            + "ORDER BY seq LIMIT #{limit}")
    List<ChatMessagePO> selectSince(@Param("sessionId") long sessionId,
                                    @Param("afterSeq") long afterSeq,
                                    @Param("limit") int limit);
}
