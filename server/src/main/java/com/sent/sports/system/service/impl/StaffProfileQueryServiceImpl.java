package com.sent.sports.system.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.system.dto.admin.AdminMeVO;
import com.sent.sports.system.mapper.StaffMapper;
import com.sent.sports.system.mapper.po.SysRolePO;
import com.sent.sports.system.mapper.po.SysStaffPO;
import com.sent.sports.system.service.PermissionService;
import com.sent.sports.system.service.StaffProfileQueryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 管理端当前员工查询实现（收口阶段）：权限点复用 P5 的 {@link PermissionService}（带短 TTL 缓存），
 * 数据范围取角色配置，未知值一律按最严的 SELF，避免前端误判可见范围。
 */
@Service
public class StaffProfileQueryServiceImpl implements StaffProfileQueryService {

    private static final String DEFAULT_SCOPE = "SELF";

    private final StaffMapper staffMapper;
    private final PermissionService permissionService;

    public StaffProfileQueryServiceImpl(StaffMapper staffMapper, PermissionService permissionService) {
        this.staffMapper = staffMapper;
        this.permissionService = permissionService;
    }

    @Override
    public AdminMeVO profile(long staffId) {
        SysStaffPO staff = staffMapper.selectById(staffId);
        if (staff == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        SysRolePO role = staffMapper.selectRoleByStaffId(staffId);
        String roleCode = role == null || role.getCode() == null ? "" : role.getCode();
        String dataScope = role == null || role.getDataScope() == null
                ? DEFAULT_SCOPE : role.getDataScope();
        List<String> permissions = new ArrayList<>(permissionService.permissionsOf(staffId));
        Collections.sort(permissions);
        return new AdminMeVO(staffId, staff.getStaffNo(), staff.getName(),
                "admin", roleCode, permissions, dataScope);
    }
}
