package com.sent.sports.finance.service;

import com.sent.sports.finance.dto.UserEnterpriseDtos;

/**
 * 用户端企业工具查询（个人中心 → 企业工具 / 我的授信 / 先采后付 / 我的合同）。
 * <p>数据源是 {@code enterprise_profile} 与 {@code contract}，一律按当前登录客户收口；
 * 没有企业档案（个人客户）返回 {@code status = NONE}，由前端展示「去申请企业认证」。</p>
 */
public interface UserEnterpriseQueryService {

    /** 当前客户的企业概况；无档案时返回 {@code NONE} 占位对象，不抛 404。 */
    UserEnterpriseDtos.OverviewVO overview(long customerId);
}
