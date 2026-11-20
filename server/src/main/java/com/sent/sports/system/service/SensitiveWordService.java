package com.sent.sports.system.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.system.dto.admin.SensitiveWordDtos;

/**
 * 违禁词库维护（V46）。
 * <p>词库是聊天消息与昵称/账号两道风控的唯一配置来源，所有写操作完成后都会
 * 重建 {@code SensitiveWordFilter} 的词树，因此「保存即生效」不需要重启服务。</p>
 */
public interface SensitiveWordService {

    /** 分页 + 筛选列表。 */
    PageResult<SensitiveWordDtos.WordVO> list(String keyword, String category, Integer enabled, PageQuery page);

    /** 顶部统计：总数 / 启用 / 停用。 */
    SensitiveWordDtos.StatsVO stats();

    /** 新增一个违禁词并立即生效。 */
    SensitiveWordDtos.WordVO create(SensitiveWordDtos.WordForm form, Long operatorId);

    /** 编辑（含启停）。 */
    SensitiveWordDtos.WordVO update(long id, SensitiveWordDtos.WordForm form);

    /** 只切启停。 */
    SensitiveWordDtos.WordVO toggle(long id, boolean enabled);

    /** 删除并立即生效。 */
    void delete(long id);

    /**
     * 按 DB 当前启用词重建过滤器。
     *
     * @return 生效词条数
     */
    int reloadFilter();
}
