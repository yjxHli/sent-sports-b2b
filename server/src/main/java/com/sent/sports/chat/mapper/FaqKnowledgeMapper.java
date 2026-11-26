package com.sent.sports.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.chat.mapper.po.FaqKnowledgePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 机器人知识库 Mapper（V59 / V60）。
 *
 * <p>两条读取口径不能混用：</p>
 * <ul>
 *   <li>{@link #selectAllEnabledOrdered()}：机器人匹配用，<b>只取启用条目</b>并按 id 升序——
 *       升序保证同一句话在权重并列时永远命中同一条（匹配策略见 {@code FaqMatcher}）；</li>
 *   <li>{@link #selectAdminPage}：管理端用，<b>不按状态过滤</b>，运营要能看到停用的条目并重新启用。</li>
 * </ul>
 */
@Mapper
public interface FaqKnowledgeMapper extends BaseMapper<FaqKnowledgePO> {

    /** 机器人候选集：只取启用且未删除的条目（十几条，直接全取，不走分页/模糊查询）。 */
    @Select("SELECT * FROM faq_knowledge WHERE status = 'ENABLED' AND deleted = 0 ORDER BY id ASC")
    List<FaqKnowledgePO> selectAllEnabledOrdered();

    /** 命中计数自增：只做运营统计，失败不影响回复（调用方按非关键路径处理）。 */
    @Update("UPDATE faq_knowledge SET hit_count = hit_count + 1 WHERE id = #{id}")
    int incrementHit(@Param("id") long id);

    /**
     * 管理端分页：关键词同时搜关键词与回复正文（运营常凭记忆搜「顺丰」找那条发货话术）。
     * <p>新改的排前面：{@code updated_at DESC} 让「刚调过的话术」留在第一页，省一次翻页。</p>
     * <p>{@code deleted = 0} 是硬条件：删掉的条目对运营也不可见，否则「删了还在」比不能删更糟。</p>
     */
    @Select({"<script>",
            "SELECT * FROM faq_knowledge WHERE deleted = 0 ",
            "<if test='keyword != null'> AND (keywords LIKE CONCAT('%', #{keyword}, '%') ",
            "     OR reply_content LIKE CONCAT('%', #{keyword}, '%')) </if>",
            "<if test='status != null'> AND status = #{status} </if>",
            "ORDER BY updated_at DESC, id DESC",
            "</script>"})
    IPage<FaqKnowledgePO> selectAdminPage(Page<FaqKnowledgePO> page,
                                          @Param("keyword") String keyword,
                                          @Param("status") String status);

    @Select("SELECT * FROM faq_knowledge WHERE id = #{id} AND deleted = 0")
    FaqKnowledgePO selectAdminById(@Param("id") long id);

    /**
     * 批量软删（V62）：只打 {@code deleted = 1}，命中次数与操作日志原文全部保留。
     * <p>{@code deleted = 0} 条件让本操作天然幂等：重复提交同一批 ID 返回 0 行，
     * 调用方按「已经删过了」成功处理，不需要先查再删（先查再删在并发下必然误报）。</p>
     */
    @Update("<script>UPDATE faq_knowledge SET deleted = 1, updated_at = NOW(3) "
            + "WHERE deleted = 0 AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int batchMarkDeleted(@Param("ids") List<Long> ids);

    /** 只改状态（含 updated_at，让列表按「最近改动」重排）。 */
    @Update("UPDATE faq_knowledge SET status = #{status}, updated_at = NOW(3) WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);

    /**
     * 写操作日志（{@code sys_operation_log}）。
     * <p>chat 模块与 system 模块无 Bean 依赖，按「共享审计表」直接落库，与评价治理同一口径：
     * 话术被谁改成什么，事后要能查。</p>
     */
    @Insert("INSERT INTO sys_operation_log "
            + "(staff_id, module, action, target_type, target_id, reason, before_json, after_json, created_by) "
            + "VALUES (#{staffId}, 'FAQ', #{action}, 'FAQ_KNOWLEDGE', #{targetId}, NULL, "
            + "#{beforeJson,jdbcType=VARCHAR}, #{afterJson,jdbcType=VARCHAR}, #{operatorId})")
    int insertFaqLog(@Param("staffId") Long staffId,
                     @Param("action") String action,
                     @Param("targetId") String targetId,
                     @Param("beforeJson") String beforeJson,
                     @Param("afterJson") String afterJson,
                     @Param("operatorId") Long operatorId);
}
