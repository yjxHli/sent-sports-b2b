package com.sent.sports.chat.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;

import java.util.List;

/**
 * 客服管理查询门面（P4-50 接口契约，跨模块可用）。
 * <p>会话/客服实体与仓储尚未建设（属 P2 领域 / P5 基础设施），当前由
 * {@code ChatAdminQueryServiceImpl} 桩实现占位；Phase 5 接入实体与数据范围（@DataScope）后替换。
 * 契约（接口签名）保持稳定。</p>
 */
public interface ChatAdminQueryService {

    /** 会话列表：待接入/进行中/已结束三态 + 分页。 */
    PageResult<?> sessions(PageQuery pageQuery);

    /** 客服工作量统计（占位，返回空列表，P5 接入真实聚合）。 */
    List<?> workload();
}
