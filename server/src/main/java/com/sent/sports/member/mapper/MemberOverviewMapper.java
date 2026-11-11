package com.sent.sports.member.mapper;

import com.sent.sports.member.mapper.po.MemberSnapshotPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会员概览读模型 Mapper（收口阶段）：会员数据没有独立表，
 * 等级 / 积分 / 成长值都落在 {@code customer}，企业授信落在 {@code enterprise_profile}，
 * 未结应收落在 {@code receivable}。此处一次 LEFT JOIN 取齐，供会员与积分首屏使用。
 * <p>只读、限定单客户，不参与任何写事务，因此不引入跨模块 Java 依赖
 * （ArchUnit 规则 4 约束的是 Java 依赖方向，不是 SQL 读面）。</p>
 */
@Mapper
public interface MemberOverviewMapper {

    /** 客户快照：不存在或已软删时返回 null。 */
    @Select("SELECT c.id AS customer_id, c.nickname, c.phone, c.type, c.level_code, "
            + "c.growth_value, c.points_balance, "
            + "e.credit_limit_cent, e.credit_used_cent, e.status AS enterprise_status "
            + "FROM customer c LEFT JOIN enterprise_profile e ON e.customer_id = c.id "
            + "WHERE c.id = #{customerId} AND c.deleted = 0")
    MemberSnapshotPO selectSnapshot(@Param("customerId") long customerId);

    /** 未结应收合计（分）= Σ(amount_cent − received_cent)，已结清的不计入。 */
    @Select("SELECT COALESCE(SUM(amount_cent - received_cent), 0) FROM receivable "
            + "WHERE customer_id = #{customerId} AND status <> 'CLOSED'")
    Long sumOpenReceivable(@Param("customerId") long customerId);
}
