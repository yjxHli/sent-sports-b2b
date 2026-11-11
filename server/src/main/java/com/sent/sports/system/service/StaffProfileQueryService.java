package com.sent.sports.system.service;

import com.sent.sports.system.dto.admin.AdminMeVO;

/**
 * 管理端当前员工查询门面（收口阶段）：跨模块只暴露该接口，控制器不直接依赖 Mapper。
 */
public interface StaffProfileQueryService {

    /** 读取员工基本资料 + 角色 + 权限点 + 数据范围；查无员工抛 NOT_FOUND。 */
    AdminMeVO profile(long staffId);
}
