package com.sent.sports.finance.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
    @Update("UPDATE enterprise_profile SET status = #{status}, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE customer_id = #{customerId}")
    int updateStatus(@Param("customerId") long customerId, @Param("status") String status);

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
}
