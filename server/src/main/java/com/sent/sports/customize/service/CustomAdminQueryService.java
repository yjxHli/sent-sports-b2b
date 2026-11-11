package com.sent.sports.customize.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;

/**
 * 定制审核查询门面（P4-41 接口契约，跨模块可用）。
 * <p>定制实体与仓储尚未建设（属 P2 领域 / P5 基础设施），当前由
 * {@code CustomAdminQueryServiceImpl} 桩实现占位；Phase 5 接入实体与数据范围（@DataScope）后替换。
 * 契约（接口签名）保持稳定。</p>
 */
public interface CustomAdminQueryService {

    /** 待审列表：按状态/超时筛选 + 分页。 */
    PageResult<?> pendingList(PageQuery pageQuery);

    /** 审核详情：图稿 + 订单上下文。 */
    Object detail(String customOrderId);
}
