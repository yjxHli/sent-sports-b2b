package com.sent.sports.coupon.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 用户持有券读写。防重靠 uk_coupon_no / uk_coupon_claim 唯一索引，不用分布式锁。 */
@Mapper
public interface CouponMapper {

    String COLUMNS = " id, customer_id AS customerId, template_id AS templateId, "
            + "coupon_no AS couponNo, name, amount_cent AS amountCent, threshold_cent AS thresholdCent, "
            + "coupon_type AS couponType, discount_type AS discountType, discount_rate AS discountRate, "
            + "scope_type AS scopeType, scope_ids AS scopeIds, customer_scope AS customerScope, "
            + "claim_key AS claimKey, status, deleted, source, reason, expire_at AS expireAt, "
            + "used_at AS usedAt, order_no AS orderNo, "
            + "created_at AS createdAt, updated_at AS updatedAt ";

    /** 发放记录列表关键字：券号 / 券名称 / 客户 ID。列表与 count 必须共用。 */
    String ISSUE_KEYWORD_WHERE = " AND (#{keyword} IS NULL OR #{keyword} = '' "
            + "OR cc.coupon_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR cc.name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR CAST(cc.customer_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%')) ";

    /** 联表用的列清单：必须带 cc 前缀，否则与 customer.id 撞名报 ambiguous column。 */
    String ISSUE_COLUMNS = " cc.id, cc.customer_id AS customerId, cc.template_id AS templateId, "
            + "cc.coupon_no AS couponNo, cc.name, cc.amount_cent AS amountCent, "
            + "cc.threshold_cent AS thresholdCent, cc.coupon_type AS couponType, "
            + "cc.discount_type AS discountType, cc.discount_rate AS discountRate, "
            + "cc.scope_type AS scopeType, cc.scope_ids AS scopeIds, cc.customer_scope AS customerScope, "
            + "cc.claim_key AS claimKey, cc.status, cc.deleted, cc.source, cc.reason, "
            + "cc.expire_at AS expireAt, cc.used_at AS usedAt, cc.order_no AS orderNo, "
            + "cc.created_at AS createdAt, cc.updated_at AS updatedAt ";

    @Insert("INSERT INTO customer_coupon (customer_id, template_id, coupon_no, name, amount_cent, "
            + "threshold_cent, coupon_type, discount_type, discount_rate, scope_type, scope_ids, "
            + "customer_scope, claim_key, status, source, reason, expire_at) VALUES "
            + "(#{customerId}, #{templateId}, #{couponNo}, #{name}, #{amountCent}, "
            + "#{thresholdCent}, #{couponType}, #{discountType}, #{discountRate}, #{scopeType}, "
            + "#{scopeIds}, #{customerScope}, #{claimKey}, #{status}, #{source}, #{reason}, #{expireAt})")
    int insert(CustomerCouponPO po);

    /** 当前客户已领取过的券模板 ID：领券中心据此把按钮置为「已领取」。 */
    @Select("SELECT DISTINCT template_id FROM customer_coupon "
            + "WHERE customer_id = #{customerId} AND claim_key IS NOT NULL AND deleted = 0")
    List<Long> selectClaimedTemplateIds(@Param("customerId") long customerId);

    @Select("SELECT " + COLUMNS + " FROM customer_coupon WHERE customer_id = #{customerId} "
            + "AND deleted = 0 ORDER BY status ASC, expire_at ASC, id DESC")
    List<CustomerCouponPO> selectByCustomer(@Param("customerId") long customerId);

    @Select("SELECT " + COLUMNS + " FROM customer_coupon WHERE id = #{id} AND customer_id = #{customerId} "
            + "AND deleted = 0")
    CustomerCouponPO selectByIdAndCustomer(@Param("id") long id, @Param("customerId") long customerId);

    /** 发放记录（管理端）：按发放时间倒序，带客户与券名。 */
    @Select("SELECT " + COLUMNS + " FROM customer_coupon WHERE deleted = 0 ORDER BY id DESC LIMIT #{limit}")
    List<CustomerCouponPO> selectRecentIssues(@Param("limit") int limit);

    /** 发放记录分页（2026-11-18 用户需求 3）：带客户昵称，软删的不再出现。 */
    @Select("SELECT " + ISSUE_COLUMNS + ", COALESCE(c.nickname, c.phone, '-') AS customerName "
            + "FROM customer_coupon cc LEFT JOIN customer c ON c.id = cc.customer_id "
            + "WHERE cc.deleted = 0 "
            + "AND (#{source} IS NULL OR #{source} = '' OR cc.source = #{source}) "
            + ISSUE_KEYWORD_WHERE
            + " ORDER BY cc.created_at DESC, cc.id DESC")
    IPage<CustomerCouponPO> selectIssuePage(Page<CustomerCouponPO> page,
                                            @Param("source") String source,
                                            @Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM customer_coupon cc "
            + "WHERE cc.deleted = 0 "
            + "AND (#{source} IS NULL OR #{source} = '' OR cc.source = #{source}) "
            + ISSUE_KEYWORD_WHERE)
    long countIssues(@Param("source") String source, @Param("keyword") String keyword);

    /** 批量软删发放记录：按主键或券号命中；券状态与订单折扣额都不改。 */
    @Update("<script>UPDATE customer_coupon SET deleted = 1, updated_at = NOW(3) WHERE deleted = 0 AND "
            + "(id IN <foreach collection='ids' item='x' open='(' separator=',' close=')'>#{x}</foreach>"
            + " OR coupon_no IN <foreach collection='ids' item='x' open='(' separator=',' close=')'>#{x}</foreach>)"
            + "</script>")
    int batchMarkDeleted(@Param("ids") List<String> ids);

    /** 核销：条件带 status='UNUSED' 保证同一张券只能使用一次（影响行数 0 视为已核销）。 */
    @Update("UPDATE customer_coupon SET status = 'USED', used_at = NOW(3), order_no = #{orderNo} "
            + "WHERE id = #{id} AND status = 'UNUSED' AND deleted = 0")
    int markUsed(@Param("id") long id, @Param("orderNo") String orderNo);
}
