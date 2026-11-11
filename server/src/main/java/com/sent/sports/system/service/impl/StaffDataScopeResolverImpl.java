package com.sent.sports.system.service.impl;

import com.sent.sports.common.mybatis.DataScopeContext;
import com.sent.sports.common.web.DataScopeResolver;
import com.sent.sports.system.mapper.StaffMapper;
import com.sent.sports.system.mapper.po.SysRolePO;
import com.sent.sports.system.mapper.po.SysStaffPO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 数据范围解析实现（P5-28）：{@code sys_role.data_scope} + 直属下属清单。
 * <p>实现放在 system 模块，端口在 common，MyBatis 拦截器因此无需感知角色表；
 * 只访问本模块 Mapper，符合 ArchUnit 规则 3/4。
 * <p>安全默认：查不到角色 / 角色未配置范围 / 未知级别，一律回落 SELF（宁可少看，不可越权）。
 */
@Service
public class StaffDataScopeResolverImpl implements DataScopeResolver {

    private final StaffMapper staffMapper;

    public StaffDataScopeResolverImpl(StaffMapper staffMapper) {
        this.staffMapper = staffMapper;
    }

    @Override
    public Optional<DataScopeContext.Scope> resolve(long staffId) {
        SysRolePO role = staffMapper.selectRoleByStaffId(staffId);
        String level = role == null ? null : role.getDataScope();
        if (level == null || level.isBlank()) {
            return Optional.of(DataScopeContext.Scope.self(staffId));
        }
        if (DataScopeContext.Scope.ALL.equalsIgnoreCase(level)) {
            return Optional.of(DataScopeContext.Scope.all(staffId));
        }
        if (DataScopeContext.Scope.TEAM.equalsIgnoreCase(level)) {
            List<Long> visible = staffMapper.selectByDataScope(DataScopeContext.Scope.TEAM, staffId)
                    .stream()
                    .map(SysStaffPO::getId)
                    .toList();
            return Optional.of(DataScopeContext.Scope.team(staffId, visible));
        }
        return Optional.of(DataScopeContext.Scope.self(staffId));
    }
}
