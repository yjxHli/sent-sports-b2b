package com.sent.sports.coupon.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 券模板读侧：注册礼、管理端可选档位与全员发放目标。 */
@Mapper
public interface CouponTemplateMapper {

    String COLUMNS = " id, template_no AS templateNo, name, amount_cent AS amountCent, "
            + "threshold_cent AS thresholdCent, "
            + "coupon_type AS couponType, discount_type AS discountType, discount_rate AS discountRate, "
            + "scope_type AS scopeType, scope_ids AS scopeIds, customer_scope AS customerScope, "
            + "claimable, claim_limit AS claimLimit, total_qty AS totalQty, claimed_qty AS claimedQty, "
            + "valid_days AS validDays, scene, status, created_by AS createdBy, deleted, "
            + "created_at AS createdAt, updated_at AS updatedAt ";

    /**
     * 联表版列清单（专供 LEFT JOIN sys_staff 取创建人姓名用）。
     * 两张表都有 id / name，不带前缀会直接抛「Column 'id' in field list is ambiguous」。
     */
    String PAGE_COLUMNS = " ct.id, ct.template_no AS templateNo, ct.name, ct.amount_cent AS amountCent, "
            + "ct.threshold_cent AS thresholdCent, "
            + "ct.coupon_type AS couponType, ct.discount_type AS discountType, ct.discount_rate AS discountRate, "
            + "ct.scope_type AS scopeType, ct.scope_ids AS scopeIds, ct.customer_scope AS customerScope, "
            + "ct.claimable, ct.claim_limit AS claimLimit, ct.total_qty AS totalQty, ct.claimed_qty AS claimedQty, "
            + "ct.valid_days AS validDays, ct.scene, ct.status, ct.created_by AS createdBy, ct.deleted, "
            + "ct.created_at AS createdAt, ct.updated_at AS updatedAt ";

    /** 管理端列表关键字：券号 / 券名称。列表与 count 必须共用同一段 SQL。 */
    String TEMPLATE_KEYWORD_WHERE = " AND (#{keyword} IS NULL OR #{keyword} = '' "
            + "OR ct.template_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR ct.name LIKE CONCAT('%', #{keyword}, '%')) ";

    @Select("<script>SELECT " + COLUMNS + " FROM coupon_template WHERE status = 'ACTIVE' AND deleted = 0 "
            + "<if test='id != null'> AND id = #{id} </if>"
            + "<if test='scene != null'> AND scene = #{scene} </if>"
            + "ORDER BY id</script>")
    List<CouponTemplatePO> selectTemplates(@Param("id") Long id, @Param("scene") String scene);

    default CouponTemplatePO selectById(long id) {
        List<CouponTemplatePO> rows = selectTemplates(id, null);
        return rows.isEmpty() ? null : rows.get(0);
    }

    default List<CouponTemplatePO> selectRegisterGifts() {
        return selectTemplates(null, "REGISTER");
    }

    /** 管理端发放下拉：含自建券，口径与券档位一致（按 id 升序） */
    default List<CouponTemplatePO> selectAdminTemplates() {
        return selectTemplates(null, "ADMIN");
    }

    /** 领券中心可领券（是否对当前客户类型可见由 service 过滤）。 */
    @Select("SELECT " + COLUMNS + " FROM coupon_template "
            + "WHERE status = 'ACTIVE' AND claimable = 1 AND deleted = 0 ORDER BY id")
    List<CouponTemplatePO> selectClaimable();

    /**
     * 管理端券模板列表（2026-11-18 用户需求 2）：带创建人姓名与分页。
     * 未传 couponType 时不过滤券型；deleted = 0 恒成立，软删的券不再出现。
     */
    @Select("SELECT " + PAGE_COLUMNS + ", COALESCE(s.name, '-') AS creatorName "
            + "FROM coupon_template ct LEFT JOIN sys_staff s ON s.id = ct.created_by "
            + "WHERE ct.deleted = 0 "
            + "AND (#{couponType} IS NULL OR #{couponType} = '' OR ct.coupon_type = #{couponType}) "
            + TEMPLATE_KEYWORD_WHERE
            + " ORDER BY ct.created_at DESC, ct.id DESC")
    IPage<CouponTemplatePO> selectTemplatePage(Page<CouponTemplatePO> page,
                                               @Param("couponType") String couponType,
                                               @Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM coupon_template ct "
            + "WHERE ct.deleted = 0 "
            + "AND (#{couponType} IS NULL OR #{couponType} = '' OR ct.coupon_type = #{couponType}) "
            + TEMPLATE_KEYWORD_WHERE)
    long countTemplates(@Param("couponType") String couponType, @Param("keyword") String keyword);

    /** 新建模板：券号由 IdGen 生成后随行写入，主键回填给调用方。 */
    @Insert("INSERT INTO coupon_template (template_no, name, amount_cent, threshold_cent, coupon_type, "
            + "discount_type, discount_rate, scope_type, scope_ids, customer_scope, claimable, claim_limit, "
            + "total_qty, claimed_qty, valid_days, scene, status, created_by, deleted) VALUES "
            + "(#{templateNo}, #{name}, #{amountCent}, #{thresholdCent}, #{couponType}, "
            + "#{discountType}, #{discountRate}, #{scopeType}, #{scopeIds}, #{customerScope}, #{claimable}, "
            + "#{claimLimit}, #{totalQty}, #{claimedQty}, #{validDays}, #{scene}, #{status}, #{createdBy}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CouponTemplatePO po);

    /** 批量软删：按主键或券号命中都行（前端传主键，兼容运营手填券号）。 */
    @Update("<script>UPDATE coupon_template SET deleted = 1, updated_at = NOW(3) WHERE deleted = 0 AND "
            + "(id IN <foreach collection='ids' item='x' open='(' separator=',' close=')'>#{x}</foreach>"
            + " OR template_no IN <foreach collection='ids' item='x' open='(' separator=',' close=')'>#{x}</foreach>)"
            + "</script>")
    int batchMarkDeleted(@Param("ids") List<String> ids);

    /**
     * 领取时先扣库存：total_qty < 0 表示不限量；返回 0 行即已领完。
     * 条件更新保证并发下不会超发。
     */
    @Update("UPDATE coupon_template SET claimed_qty = claimed_qty + 1 "
            + "WHERE id = #{id} AND status = 'ACTIVE' AND deleted = 0 "
            + "AND (total_qty < 0 OR claimed_qty < total_qty)")
    int increaseClaimed(@Param("id") long id);

    /** 全员发放：只发给有效客户，避免给已删除账号发券。 */
    @Select("SELECT DISTINCT customer_id FROM customer WHERE deleted = 0 AND status = 'ACTIVE'")
    List<Long> selectAllCustomerIds();

    /** 企业专属券的全员发放：只发给企业客户，个人客户发过去也用不了。 */
    @Select("SELECT DISTINCT customer_id FROM customer "
            + "WHERE deleted = 0 AND status = 'ACTIVE' AND type = 'ENTERPRISE'")
    List<Long> selectAllEnterpriseCustomerIds();
}
