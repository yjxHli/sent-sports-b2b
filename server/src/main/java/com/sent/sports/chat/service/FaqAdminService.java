package com.sent.sports.chat.service;

import com.sent.sports.chat.dto.admin.AdminFaqDtos;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;

import java.util.List;

/**
 * 智能客服话术配置（V60）：管理端知识库 CRUD + 缓存热更新。
 *
 * <p>与 {@code BotReplyUseCase} 的关系：写在这里、读在那里。任何写方法返回前都必须让
 * 知识库快照失效（{@code FaqKnowledgeRepository#invalidateCache()}），否则客户下一条消息
 * 还在用旧话术——这类问题不报错、不告警，只能靠投诉发现，所以写成接口级约定。</p>
 */
public interface FaqAdminService {

    /**
     * 分页查询（管理端看全部状态）。
     *
     * @param keyword 关键词 / 回复正文模糊搜，空白视为不过滤
     * @param status  ENABLED / DISABLED，空白视为不过滤
     */
    PageResult<AdminFaqDtos.FaqVO> list(String keyword, String status, PageQuery page);

    /** 新增话术：落库后立即对机器人生效。 */
    AdminFaqDtos.FaqVO create(AdminFaqDtos.FaqCmd cmd, Long operatorId);

    /** 修改话术：落库后立即对机器人生效。 */
    AdminFaqDtos.FaqVO update(long id, AdminFaqDtos.FaqCmd cmd, Long operatorId);

    /** 启用 / 停用：停用只是暂时不答，条目与命中次数保留。 */
    AdminFaqDtos.FaqVO updateStatus(long id, String status, Long operatorId);

    /**
     * 批量删除话术（V62）：与发票管理同口径——只打 {@code deleted} 软删标记，
     * 不改命中次数、不动物理行；删完必须让知识库快照失效，否则机器人还在用被删的话术回复。
     *
     * @param ids 待删条目 ID（前端字符串数组，服务层归一为去重升序的数字）
     * @return 实际打上删除标记的条数（0 表示已删过，按幂等成功处理）
     */
    int delete(List<String> ids, Long operatorId);
}
