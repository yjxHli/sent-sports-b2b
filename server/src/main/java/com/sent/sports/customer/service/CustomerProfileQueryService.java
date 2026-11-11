package com.sent.sports.customer.service;

import com.sent.sports.customer.dto.user.MeVO;

/**
 * 用户端个人资料查询门面（收口阶段）：跨模块可用。
 * <p>供用户端 {@code /api/me} 读取当前客户的基本信息、会员等级与积分，
 * 实现依赖 customer 模块的 {@code CustomerMapper}（真实表查询）。</p>
 */
public interface CustomerProfileQueryService {

    /** 按客户主键读取个人资料；客户不存在或已删除抛 NOT_FOUND。 */
    MeVO profile(long customerId);
}
