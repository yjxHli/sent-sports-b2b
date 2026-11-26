package com.sent.sports.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.chat.mapper.po.ChatSessionNotePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ChatSessionNoteMapper extends BaseMapper<ChatSessionNotePO> {

    @Select("SELECT * FROM chat_session_note WHERE session_id = #{sessionId} LIMIT 1")
    ChatSessionNotePO selectBySessionId(@Param("sessionId") long sessionId);

    /**
     * 同会话重复填写走显式列更新（updateById 会跳过 null，必填字段不允许被抹掉）。
     * V61 新增字段一并写入：可空字段传 null 表示清空，回访标记 NOT NULL 直接落库。
     * V65 增加回访联系方式与提交状态：草稿也能落库，只有 SUBMITTED 进客服审查。
     */
    @Update("UPDATE chat_session_note SET summary = #{summary}, category = #{category}, "
            + "sub_category = #{subCategory}, customer_demand = #{customerDemand}, "
            + "verified_info = #{verifiedInfo}, issue_info = #{issueInfo}, result = #{result}, "
            + "solution = #{solution}, need_followup = #{needFollowup}, "
            + "followup_time = #{followupTime}, followup_contact = #{followupContact}, "
            + "attachments_json = #{attachmentsJson}, submit_status = #{submitStatus}, "
            + "submitted_at = #{submittedAt}, "
            + "staff_id = #{staffId} WHERE session_id = #{sessionId}")
    int updateBySessionId(@Param("sessionId") long sessionId, @Param("staffId") long staffId,
                          @Param("summary") String summary, @Param("category") String category,
                          @Param("subCategory") String subCategory,
                          @Param("customerDemand") String customerDemand,
                          @Param("verifiedInfo") String verifiedInfo,
                          @Param("issueInfo") String issueInfo, @Param("result") String result,
                          @Param("solution") String solution,
                          @Param("needFollowup") boolean needFollowup,
                          @Param("followupTime") LocalDateTime followupTime,
                          @Param("followupContact") String followupContact,
                          @Param("attachmentsJson") String attachmentsJson,
                          @Param("submitStatus") String submitStatus,
                          @Param("submittedAt") LocalDateTime submittedAt);
}
