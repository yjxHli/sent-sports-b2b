package com.sent.sports.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.system.mapper.po.SysRolePO;
import com.sent.sports.system.mapper.po.SysStaffPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 员工 / 角色 Mapper（P5-10）：CRUD 由 {@link BaseMapper} 提供，此处只补三类业务必需查询。
 * <ul>
 *   <li>登录：按手机号 / 工号取带密码散列的员工行（含 {@code deleted = 0} 过滤）；</li>
 *   <li>数据范围：按角色 {@code ALL / TEAM / SELF} 裁剪可见员工，供 {@code DataScopeInterceptor}（P5-28）与列表页复用；</li>
 *   <li>令牌吊销：{@code token_version} 单调自增，使旧令牌立即失效。</li>
 * </ul>
 * 只操作 system 模块自有表，不跨模块取数，保持模块内聚。
 */
@Mapper
public interface StaffMapper extends BaseMapper<SysStaffPO> {

    /** 登录用：按手机号读取未删除员工。 */
    @Select("SELECT * FROM sys_staff WHERE phone = #{phone} AND deleted = 0")
    SysStaffPO selectByPhone(@Param("phone") String phone);

    /** 登录用：按工号读取未删除员工。 */
    @Select("SELECT * FROM sys_staff WHERE staff_no = #{staffNo} AND deleted = 0")
    SysStaffPO selectByStaffNo(@Param("staffNo") String staffNo);

    /** 读取员工所属角色（权限点缓存与数据范围的来源）。 */
    @Select("SELECT r.* FROM sys_role r JOIN sys_staff s ON s.role_id = r.id "
            + "WHERE s.id = #{staffId} AND s.deleted = 0")
    SysRolePO selectRoleByStaffId(@Param("staffId") long staffId);

    /**
     * 数据范围查询：ALL 全量、TEAM 本人及其直属下级、SELF 仅本人。
     * 未知 scope 一律按最严的 SELF 处理（默认拒绝，避免越权）。
     */
    @Select("<script>"
            + "SELECT * FROM sys_staff WHERE deleted = 0"
            + "<choose>"
            + "<when test=\"scope == 'ALL'\"> </when>"
            + "<when test=\"scope == 'TEAM'\"> AND (id = #{staffId} OR leader_id = #{staffId})</when>"
            + "<otherwise> AND id = #{staffId}</otherwise>"
            + "</choose>"
            + " ORDER BY id"
            + "</script>")
    List<SysStaffPO> selectByDataScope(@Param("scope") String scope, @Param("staffId") long staffId);

    /** 令牌吊销：版本号自增，返回受影响行数（0 表示员工不存在）。 */
    @Update("UPDATE sys_staff SET token_version = token_version + 1 WHERE id = #{staffId} AND deleted = 0")
    int bumpTokenVersion(@Param("staffId") long staffId);

    /** 读取当前令牌版本（{@code TokenVersionLookup} 的实现依此判断令牌是否已被吊销）。 */
    @Select("SELECT token_version FROM sys_staff WHERE id = #{staffId} AND deleted = 0")
    Long selectTokenVersion(@Param("staffId") long staffId);

    /** 登录成功后清零失败计数与锁定时间。 */
    @Update("UPDATE sys_staff SET fail_count = 0, locked_until = NULL, last_login_at = NOW(3) "
            + "WHERE id = #{staffId} AND deleted = 0")
    int markLoginSuccess(@Param("staffId") long staffId);

    /** 登录失败累加；达到阈值时由业务层另行写入 {@code locked_until}。 */
    @Update("UPDATE sys_staff SET fail_count = fail_count + 1 WHERE id = #{staffId} AND deleted = 0")
    int increaseFailCount(@Param("staffId") long staffId);
}
