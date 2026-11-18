package com.sent.sports.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.customer.mapper.po.CustomerPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 客户 Mapper：映射 V3 的 {@code customer} 表。
 * <p>除个人资料读取（{@link #selectById}）外，成长值累加也收口在这里——
 * 它需要与 {@code growth_log} 落账同处一个事务，且用「条件更新」而非聚合覆盖，
 * 避免与并行的其它客户字段写入互相覆盖。</p>
 */
@Mapper
public interface CustomerMapper extends BaseMapper<CustomerPO> {

    /**
     * 用户端改昵称/头像（2026-11-17）。显式写两列：MyBatis-Plus 的 updateById 会跳过 null，
     * 客户想清空头像时根本写不进去。
     */
    @Update("UPDATE customer SET nickname = #{nickname}, avatar_file_id = #{avatarFileId} "
            + "WHERE id = #{customerId} AND deleted = 0")
    int updateProfile(@Param("customerId") long customerId,
                      @Param("nickname") String nickname,
                      @Param("avatarFileId") String avatarFileId);

    /** 成长值累加（growth_log 唯一键确认是首次发放后才调用，不重复加钱）。 */
    @Update("UPDATE customer SET growth_value = growth_value + #{delta} WHERE id = #{customerId}")
    int addGrowth(@Param("customerId") long customerId, @Param("delta") long delta);

    /**
     * 管理端客户分页：姓名/手机号/客户编号/客户 ID 模糊匹配，按会员等级与客户类型收窄；已软删的客户不返回。
     * 数据范围（ALL/TEAM/SELF）由 {@code DataScopeInterceptor} 在 SQL 外侧追加，此处不写死归属条件。
     * <p>type 下推到 SQL（而非前端过滤）：PageQuery 单页上限 100，前端先截断再过滤会漏数据
     * （企业审核与授信页曾因此只看到 50 / 真实 53 家）。</p>
     * <p>客户 ID 也参与匹配：企业审核与授信页经常只拿到一个 91xxx 的客户号，
     * 手动翻页找太慢，把 BIGINT 转字符串参与 LIKE 即可。</p>
     */
    @Select("<script>"
            + "SELECT c.*, e.credit_limit_cent, e.credit_used_cent, e.status AS enterprise_status, "
            + "e.company_name, e.license_no, e.license_url "
            + "FROM customer c LEFT JOIN enterprise_profile e ON e.customer_id = c.id "
            + "WHERE c.deleted = 0 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + " AND (c.nickname LIKE CONCAT('%', #{keyword}, '%')"
            + " OR c.phone LIKE CONCAT('%', #{keyword}, '%')"
            + " OR c.customer_no LIKE CONCAT('%', #{keyword}, '%')"
            + " OR CAST(c.id AS CHAR) LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if> "
            + "<if test=\"level != null and level != ''\"> AND c.level_code = #{level}</if> "
            + "<if test=\"type != null and type != ''\"> AND c.type = #{type}</if> "
            + "ORDER BY c.id DESC"
            + "</script>")
    IPage<CustomerPO> selectAdminPage(Page<CustomerPO> page,
                                      @Param("keyword") String keyword,
                                      @Param("level") String level,
                                      @Param("type") String type);

    // ---- 客户运营动作（P6 收口）：条件更新 + 落流水，全部命中真库 customer 表 ----

    /** 改派归属：条件更新，返回受影响行数；软删客户不会被改派。 */
    @Update("UPDATE customer SET owner_staff_id = #{toStaffId} "
            + "WHERE id = #{customerId} AND deleted = 0")
    int updateOwnerStaff(@Param("customerId") long customerId, @Param("toStaffId") long toStaffId);

    /** 积分调整：增量更新（不按聚合值覆盖，避免并发丢更新）。 */
    @Update("UPDATE customer SET points_balance = points_balance + #{delta} "
            + "WHERE id = #{customerId} AND deleted = 0")
    int addPoints(@Param("customerId") long customerId, @Param("delta") long delta);

    /** 人工授级：等级码与有效期一起写（有效期 NULL = 长期有效），历史订单/专享价快照不受影响。 */
    @Update("UPDATE customer SET level_code = #{level}, level_valid_to = #{levelValidTo} "
            + "WHERE id = #{customerId} AND deleted = 0")
    int updateLevelGrant(@Param("customerId") long customerId,
                         @Param("level") String level,
                         @Param("levelValidTo") LocalDateTime levelValidTo);

    /**
     * 成长值驱动的等级提升：只升不降（等级码顺序用 {@code FIND_IN_SET} 判定，
     * 已授级或已是更高档时不覆盖），返回 0 行即代表无需变更。
     */
    @Update("UPDATE customer SET level_code = #{level} "
            + "WHERE id = #{customerId} AND deleted = 0 "
            + "AND FIND_IN_SET(COALESCE(level_code, 'NORMAL'), 'NORMAL,LV1,LV2,LV3') "
            + "< FIND_IN_SET(#{level}, 'NORMAL,LV1,LV2,LV3')")
    int promoteLevel(@Param("customerId") long customerId, @Param("level") String level);

    /** 积分流水一行（point_log 真表；source_type / source_id 承载业务类型与单号）。 */
    record PointLogRow(long customerId, long delta, long balanceAfter, String type,
                       String sourceType, String sourceId, long operatorId) {
    }

    @Insert("INSERT INTO point_log "
            + "(customer_id, change_value, balance_after, type, source_type, source_id, created_by) "
            + "VALUES (#{customerId}, #{delta}, #{balanceAfter}, #{type}, "
            + "#{sourceType}, #{sourceId}, #{operatorId})")
    int insertPointLog(PointLogRow row);

    /** 改派 / 授级操作流水（sys_operation_log；after_json 记录变更前后与授级有效期）。 */
    record OperationLogRow(Long staffId, String module, String action, String targetType,
                           String targetId, String reason, String afterJson, Long operatorId) {
    }

    @Insert("INSERT INTO sys_operation_log "
            + "(staff_id, module, action, target_type, target_id, reason, after_json, created_by) "
            + "VALUES (#{staffId}, #{module}, #{action}, #{targetType}, #{targetId}, "
            + "#{reason}, #{afterJson}, #{operatorId})")
    int insertOperationLog(OperationLogRow row);
}
