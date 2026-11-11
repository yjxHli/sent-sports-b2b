package com.sent.sports.common.web;

import com.sent.sports.common.mybatis.DataScopeContext;

import java.util.Optional;

/**
 * 数据范围解析端口（P5-28）：由 system 模块读取 {@code sys_role.data_scope} 与直属下属实现。
 * <p>放在 common 是为了让 MyBatis 拦截器与 Web 入口都不依赖业务模块（ArchUnit 规则 3）。
 */
public interface DataScopeResolver {

    /** 解析某员工的数据范围；无角色时实现方应保守回落到 SELF，而不是返回 empty 造成全量可见。 */
    Optional<DataScopeContext.Scope> resolve(long staffId);
}
