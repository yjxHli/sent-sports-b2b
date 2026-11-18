package com.sent.sports.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.chat.mapper.po.ChatSessionNotePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionNoteMapper extends BaseMapper<ChatSessionNotePO> {

    @Select("SELECT * FROM chat_session_note WHERE session_id = #{sessionId} LIMIT 1")
    ChatSessionNotePO selectBySessionId(@Param("sessionId") long sessionId);

    /** 同会话重复填写走显式列更新（updateById 会跳过 null，小结字段均不允许为空）。 */
    @Update("UPDATE chat_session_note SET summary = #{summary}, category = #{category}, "
            + "issue_info = #{issueInfo}, result = #{result}, solution = #{solution}, "
            + "staff_id = #{staffId} WHERE session_id = #{sessionId}")
    int updateBySessionId(@Param("sessionId") long sessionId, @Param("staffId") long staffId,
                          @Param("summary") String summary, @Param("category") String category,
                          @Param("issueInfo") String issueInfo, @Param("result") String result,
                          @Param("solution") String solution);
}
