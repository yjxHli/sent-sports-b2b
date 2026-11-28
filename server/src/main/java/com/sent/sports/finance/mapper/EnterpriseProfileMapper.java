package com.sent.sports.finance.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.finance.dto.admin.AdminFinanceDtos;
import com.sent.sports.finance.mapper.po.MyEnterpriseRow;
import com.sent.sports.finance.service.EnterpriseProfileQueryService;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 企业授信档案 Mapper（验收缺口补齐）：把 {@code enterprise_profile.credit_used_cent}
 * 作为「已占用额度」的真库落点，与内存 {@code CreditLineRepository} 的域对象保持同步。
 * <p>用 {@code GREATEST(..., 0)} 做下限保护，避免冲销/重复核销把占用额度写成负数。</p>
 */
@Mapper
public interface EnterpriseProfileMapper {

    /** 个人客户没有企业档案，返回 null 表示不参与授信占用。 */
    @Select("SELECT credit_limit_cent FROM enterprise_profile WHERE customer_id = #{customerId}")
    Long selectCreditLimit(@Param("customerId") long customerId);

    /** 已占用授信额度（分）；无档案返回 null。 */
    @Select("SELECT credit_used_cent FROM enterprise_profile WHERE customer_id = #{customerId}")
    Long selectCreditUsed(@Param("customerId") long customerId);

    /** 订单完成形成应收时占用额度。 */
    @Update("UPDATE enterprise_profile SET credit_used_cent = credit_used_cent + #{amountCent} "
            + "WHERE customer_id = #{customerId}")
    int addCreditUsed(@Param("customerId") long customerId, @Param("amountCent") long amountCent);

    /** 回款核销后释放对应占用。 */
    @Update("UPDATE enterprise_profile SET credit_used_cent = GREATEST(credit_used_cent - #{amountCent}, 0) "
            + "WHERE customer_id = #{customerId}")
    int subtractCreditUsed(@Param("customerId") long customerId, @Param("amountCent") long amountCent);

    /** 商城企业注册：新建 PENDING 档案，license_url 只存 fileId（二进制不进 JSON 列）。 */
    @Insert("INSERT INTO enterprise_profile (customer_id, company_name, license_no, license_url, "
            + "invoice_title, tax_no, status) "
            + "VALUES (#{customerId}, #{companyName}, #{creditCode}, #{licenseFileId}, "
            + "#{invoiceTitle}, #{taxNo}, 'PENDING')")
    int insertPending(@Param("customerId") long customerId,
                      @Param("companyName") String companyName,
                      @Param("creditCode") String creditCode,
                      @Param("licenseFileId") String licenseFileId,
                      @Param("invoiceTitle") String invoiceTitle,
                      @Param("taxNo") String taxNo);

    /** 演示数据：为企业客户建档时一并写入授信档案。 */
    @Insert("INSERT INTO enterprise_profile (customer_id, company_name, license_no, invoice_title, tax_no, "
            + "credit_limit_cent, credit_used_cent, payment_term_days, status) "
            + "VALUES (#{customerId}, #{companyName}, #{licenseNo}, #{invoiceTitle}, #{taxNo}, "
            + "#{creditLimitCent}, #{creditUsedCent}, #{paymentTermDays}, #{status})")
    void insertProfile(@Param("customerId") long customerId,
                       @Param("companyName") String companyName,
                       @Param("licenseNo") String licenseNo,
                       @Param("invoiceTitle") String invoiceTitle,
                       @Param("taxNo") String taxNo,
                       @Param("creditLimitCent") long creditLimitCent,
                       @Param("creditUsedCent") long creditUsedCent,
                       @Param("paymentTermDays") int paymentTermDays,
                       @Param("status") String status);

    /**
     * 企业认证审核落库：只改 {@code status}。
     * <p>返回 0 说明没有该企业的认证档案——调用方据此抛 40401，
     * 而不是让它变成「系统异常」。</p>
     */
    @Update("UPDATE enterprise_profile SET status = #{status}, audit_remark = #{remark}, "
            + "audited_at = FROM_UNIXTIME(#{auditedAtMillis} / 1000), updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE customer_id = #{customerId}")
    int updateAudit(@Param("customerId") long customerId, @Param("status") String status,
                    @Param("remark") String remark, @Param("auditedAtMillis") long auditedAtMillis);

    /** 用户端审核进度：手机号 + 验证码通过后查询，只返回未软删档案。 */
    @Select("SELECT p.customer_id AS customerId, c.phone AS phone, p.company_name AS companyName, "
            + "p.license_no AS creditCode, p.license_url AS licenseFileId, p.status AS status, "
            + "p.audit_remark AS auditRemark, UNIX_TIMESTAMP(p.audited_at) * 1000 AS auditedAtMillis, "
            + "UNIX_TIMESTAMP(p.created_at) * 1000 AS submittedAtMillis "
            + "FROM enterprise_profile p JOIN customer c ON c.id = p.customer_id "
            + "WHERE c.phone = #{phone} AND p.deleted = 0 LIMIT 1")
    EnterpriseProfileQueryService.AuditProgress selectAuditProgressByPhone(@Param("phone") String phone);

    /**
     * 用户端企业档案（当前登录客户）：软删档案查不到，返回 null 表示「还不是企业客户」。
     * <p>只取本人可见字段，授信额度与账期在这里一次性带出，前端不必再调管理端接口。</p>
     */
    @Select("SELECT p.customer_id AS customerId, p.company_name AS companyName, "
            + "p.license_no AS creditCode, p.license_url AS licenseFileId, p.status AS status, "
            + "p.credit_limit_cent AS creditLimitCent, p.credit_used_cent AS creditUsedCent, "
            + "p.payment_term_days AS paymentTermDays, p.credit_expire_at AS creditExpireAt, "
            + "p.audit_remark AS auditRemark, p.audited_at AS auditedAt, "
            + "p.created_at AS submittedAt "
            + "FROM enterprise_profile p WHERE p.customer_id = #{customerId} AND p.deleted = 0")
    MyEnterpriseRow selectMyProfile(@Param("customerId") long customerId);

    /** 管理端企业审核列表批量软删：只打标记，不动授信额度与状态。 */
    @Update({"<script>",
            "UPDATE enterprise_profile SET deleted = 1 WHERE deleted = 0 AND customer_id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    int batchMarkDeleted(@Param("ids") List<Long> ids);

    /**
     * 授信额度落库（已有档案）：额度 / 账期 / 到期时间一次写全，已用额度保持不变。
     * <p>此前授信只写进程内 Map，客户列表读的是本表 → 界面永远停在「未授信」。</p>
     */
    @Update("UPDATE enterprise_profile SET credit_limit_cent = #{limitCent}, "
            + "payment_term_days = #{termDays}, "
            + "credit_expire_at = FROM_UNIXTIME(#{expireAtMillis} / 1000), "
            + "updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE customer_id = #{customerId}")
    int updateCredit(@Param("customerId") long customerId,
                     @Param("limitCent") long limitCent,
                     @Param("termDays") long termDays,
                     @Param("expireAtMillis") long expireAtMillis);

    /** 授信额度落库（无档案时补建）：企业名称由调用方兜底，避免 NOT NULL 列报错。 */
    @Insert("INSERT INTO enterprise_profile (customer_id, company_name, credit_limit_cent, credit_used_cent, "
            + "payment_term_days, credit_expire_at, status) "
            + "VALUES (#{customerId}, #{companyName}, #{limitCent}, 0, #{termDays}, "
            + "FROM_UNIXTIME(#{expireAtMillis} / 1000), 'ACTIVE')")
    int insertCredit(@Param("customerId") long customerId,
                     @Param("companyName") String companyName,
                     @Param("limitCent") long limitCent,
                     @Param("termDays") long termDays,
                     @Param("expireAtMillis") long expireAtMillis);

    /** 管理端企业审核列表：企业基本信息 + 审核意见，列表与 count 共用同一 WHERE。 */
    @Select("<script>"
            + "SELECT p.customer_id AS customerId, c.customer_no AS customerNo, c.phone AS phone, "
            + "p.company_name AS companyName, p.license_no AS creditCode, p.license_url AS licenseFileId, "
            + "p.status AS status, p.audit_remark AS auditRemark, "
            + "UNIX_TIMESTAMP(p.audited_at) * 1000 AS auditedAtMillis, "
            + "UNIX_TIMESTAMP(p.created_at) * 1000 AS submittedAtMillis, "
            + "p.credit_limit_cent AS creditLimitCent, p.credit_used_cent AS creditUsedCent "
            + "FROM enterprise_profile p JOIN customer c ON c.id = p.customer_id "
            + "WHERE p.deleted = 0 AND c.deleted = 0 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + " AND (p.company_name LIKE CONCAT('%', #{keyword}, '%')"
            + " OR p.license_no LIKE CONCAT('%', #{keyword}, '%')"
            + " OR c.phone LIKE CONCAT('%', #{keyword}, '%')"
            + " OR CAST(p.customer_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if> "
            + "<if test=\"status != null and status != ''\"> AND p.status = #{status} </if> "
            + "ORDER BY p.customer_id DESC"
            + "</script>")
    IPage<AdminFinanceDtos.EnterpriseAuditRow> selectAuditPage(Page<AdminFinanceDtos.EnterpriseAuditRow> page,
                                                               @Param("keyword") String keyword,
                                                               @Param("status") String status);

    @Select("<script>"
            + "SELECT COUNT(*) FROM enterprise_profile p JOIN customer c ON c.id = p.customer_id "
            + "WHERE p.deleted = 0 AND c.deleted = 0 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + " AND (p.company_name LIKE CONCAT('%', #{keyword}, '%')"
            + " OR p.license_no LIKE CONCAT('%', #{keyword}, '%')"
            + " OR c.phone LIKE CONCAT('%', #{keyword}, '%')"
            + " OR CAST(p.customer_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if> "
            + "<if test=\"status != null and status != ''\"> AND p.status = #{status} </if> "
            + "</script>")
    long countAudit(@Param("keyword") String keyword, @Param("status") String status);
}
