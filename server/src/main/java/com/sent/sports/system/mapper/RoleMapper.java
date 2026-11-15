package com.sent.sports.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.system.mapper.po.SysRolePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper（收口阶段）：角色权限矩阵页此前读的是空桩 {@code List.of()}，
 * 这里补齐管理端所需的三个读操作，写侧（角色-权限）由 {@link PermissionMapper} 负责。
 */
@Mapper
public interface RoleMapper extends BaseMapper<SysRolePO> {

    /** 全部角色（按 id 升序，内置角色在前），用于权限矩阵左栏。 */
    @Select("SELECT * FROM sys_role ORDER BY id")
    List<SysRolePO> selectAllRoles();

    /** 按编码取角色；不存在返回 null（业务侧据此抛 404）。 */
    @Select("SELECT * FROM sys_role WHERE code = #{code}")
    SysRolePO selectByCode(@Param("code") String code);

    /** 按编码取角色主键（角色-权限写入时用）。 */
    @Select("SELECT id FROM sys_role WHERE code = #{code}")
    Long selectIdByCode(@Param("code") String code);
}
