package com.sent.sports.customize.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customize.dto.CustomDtos;

/**
 * 定制查询门面（P4-23 接口契约，跨模块可用）。
 * <p>定制单/图稿实体与仓储尚未建设（属 P2 领域 / P5 DB 范畴），当前由
 * {@code CustomQueryServiceImpl} 桩实现占位；Phase 5 接入实体与数据范围（@DataScope）后替换。
 * 契约（接口签名）保持稳定。</p>
 */
public interface CustomQueryService {

    /** 定制单详情：含状态、版本、驳回次数与驳回原因。 */
    CustomDtos.CustomVO detail(String customOrderId);

    /** 用户端「我的定制单」分页：联查订单状态与最新图稿版本。 */
    PageResult<CustomDtos.MyCustomOrderVO> myOrders(long customerId, PageQuery pageQuery);
}
