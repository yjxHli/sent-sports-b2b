package com.sent.sports.customer.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customer.dto.admin.AdminCustomerDtos;

/**
 * 客户查询门面（P4-44 接口契约，跨模块可用）。
 * <p>列表已接入 {@code CustomerMapper} 真分页（关键字命中昵称/手机号/客户编号，可按会员等级收窄）；
 * 数据范围由 MyBatis 的 {@code DataScopeInterceptor} 统一追加，本层不写死归属条件。</p>
 */
public interface CustomerQueryService {

    /** 客户列表：关键字 + 等级 + 客户类型（PERSON / ENTERPRISE）筛选 + 分页。 */
    PageResult<AdminCustomerDtos.CustomerRow> list(PageQuery pageQuery, String keyword,
                                                   String level, String type);

    /** 客户详情：订单/售后/会话/应收四 Tab 上下文。 */
    Object detail(long customerId);
}
