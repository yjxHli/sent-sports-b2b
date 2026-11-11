package com.sent.sports.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.system.mapper.po.SysPermissionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限点 Mapper（P5-27）：只提供「按角色取权限码」与「按员工取角色」两个读操作，
 * 角色-权限的增删由后台管理场景直接走角色表，不需要复杂查询。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<SysPermissionPO> {

    /** 某角色拥有的权限码（去重后返回，供缓存）。 */
    @Select("SELECT DISTINCT p.code FROM sys_permission p "
            + "JOIN sys_role_permission rp ON rp.permission_id = p.id "
            + "WHERE rp.role_id = #{roleId} ORDER BY p.code")
    List<String> selectCodesByRoleId(@Param("roleId") long roleId);

    /** 某员工所属角色主键（无角色返回 null）。 */
    @Select("SELECT role_id FROM sys_staff WHERE id = #{staffId} AND deleted = 0")
    Long selectRoleIdByStaffId(@Param("staffId") long staffId);
}
