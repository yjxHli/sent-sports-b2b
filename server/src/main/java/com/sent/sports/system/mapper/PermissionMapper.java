package com.sent.sports.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.system.mapper.po.SysPermissionPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
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

    // ------------------------------------------------------------------
    // 角色权限矩阵写入：整组替换（先删后插），单事务内完成
    // ------------------------------------------------------------------

    /**
     * 登记一个权限点（幂等）：前端权限码目录来自 {@code @RequirePerm} 与菜单 meta.perm，
     * 库里不一定先有该行；{@code INSERT IGNORE} 依赖 {@code uk_perm_code} 保证重复调用安全。
     */
    @Insert("INSERT IGNORE INTO sys_permission (code, name, module, type) "
            + "VALUES (#{code}, #{code}, 'ADMIN', 'ACTION')")
    int ensurePermission(@Param("code") String code);

    /** 按权限码取主键。 */
    @Select("SELECT id FROM sys_permission WHERE code = #{code}")
    Long selectPermissionId(@Param("code") String code);

    /** 清空某角色的全部权限（矩阵保存 = 整组替换）。 */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteRolePerms(@Param("roleId") long roleId);

    /** 给角色挂一个权限点（幂等）。 */
    @Insert("INSERT IGNORE INTO sys_role_permission (role_id, permission_id, created_at) "
            + "VALUES (#{roleId}, #{permissionId}, NOW(3))")
    int insertRolePerm(@Param("roleId") long roleId, @Param("permissionId") long permissionId);
}
