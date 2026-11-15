package com.sent.sports.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.notify.mapper.po.UserMessagePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 站内信 Mapper。
 * <p>所有语句都带 {@code customer_id} 条件：越权只能从「查不到」体现，不靠上层再判一次。</p>
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessagePO> {

    /** 我的消息分页：type 为空时全量，非空时按类型筛选。 */
    @Select({"<script>",
            "SELECT * FROM user_message WHERE customer_id = #{customerId}",
            "<if test='msgType != null and msgType != \"\"'> AND msg_type = #{msgType}</if>",
            "ORDER BY id DESC",
            "</script>"})
    IPage<UserMessagePO> selectMyPage(Page<UserMessagePO> page,
                                     @Param("customerId") long customerId,
                                     @Param("msgType") String msgType);

    /** 未读数（红点用）。 */
    @Select("SELECT COUNT(*) FROM user_message WHERE customer_id = #{customerId} AND read_flag = 0")
    long countUnread(@Param("customerId") long customerId);

    /** 标记单条已读；返回 0 表示消息不存在或不属于该客户。 */
    @Update("UPDATE user_message SET read_flag = 1 WHERE id = #{id} AND customer_id = #{customerId} "
            + "AND read_flag = 0")
    int markRead(@Param("customerId") long customerId, @Param("id") long id);

    /** 全部标记已读，返回本次受影响条数。 */
    @Update("UPDATE user_message SET read_flag = 1 WHERE customer_id = #{customerId} AND read_flag = 0")
    int markAllRead(@Param("customerId") long customerId);

    /**
     * 删除单条：带 {@code customer_id} 条件，越权删除只会命中 0 行（不泄露他人消息是否存在）。
     * <p>站内信是异步通知，物理删除即可，不需要软删标记——用户主动删掉的内容没必要留痕。</p>
     */
    @Delete("DELETE FROM user_message WHERE id = #{id} AND customer_id = #{customerId}")
    int deleteMy(@Param("customerId") long customerId, @Param("id") long id);

    /** 一键清空当前客户的全部站内信，返回实际删除条数。 */
    @Delete("DELETE FROM user_message WHERE customer_id = #{customerId}")
    int deleteAllMy(@Param("customerId") long customerId);
}
