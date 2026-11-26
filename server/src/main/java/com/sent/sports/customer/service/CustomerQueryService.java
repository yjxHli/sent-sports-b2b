package com.sent.sports.customer.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customer.dto.admin.AdminCustomerDtos;
import com.sent.sports.customer.service.dto.CustomerBrief;
import com.sent.sports.customer.service.dto.CustomerContact;

import java.util.Map;

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

    /** 客户类型 PERSON / ENTERPRISE；客户不存在或已软删返回 null。 */
    String typeOf(long customerId);

    /**
     * 批量取客户展示信息（昵称 + 头像 fileId），供客服工作台等场景标识「进线用户是谁」。
     *
     * <p>一次查询，按 ID 建索引返回；不存在的 ID 不会出现在结果里，由调用方兜底。
     * 空集合直接返回空 Map，不打库。</p>
     */
    Map<Long, CustomerBrief> briefsOf(java.util.Collection<Long> customerIds);

    /** 服务工单只读基础信息：昵称 + 联系方式；客户不存在/已软删返回 null。 */
    CustomerContact contactOf(long customerId);
}
