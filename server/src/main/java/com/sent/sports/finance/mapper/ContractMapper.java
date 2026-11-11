package com.sent.sports.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.finance.mapper.po.ContractListItemPO;
import com.sent.sports.finance.mapper.po.ContractPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 合同 Mapper（收口阶段）：金额与状态取自 V10 补列，列表分页由 MyBatis-Plus {@link Page} 驱动。
 *
 * <p><b>到期状态下推</b>：前端「到期状态」选项是 {@code EXPIRED / SOON / NORMAL}，
 * 这不是 {@code contract.status} 的值域（那是 ACTIVE / EXPIRED 这类单据状态），
 * 而是由 {@code expire_date} 与当天比较得出的派生状态。此前前端先取一页再过滤，
 * 一翻页就把「本页之外」的合同全部丢掉，所以必须在 SQL 里过滤、并把 count 用同一条件。</p>
 */
@Mapper
public interface ContractMapper extends BaseMapper<ContractPO> {

    /** 到期状态判定片段：EXPIRED 已过期 / SOON 30 天内到期 / NORMAL 30 天以后或无到期日。 */
    String DUE_STATE_WHERE = " AND (#{dueState} IS NULL OR #{dueState} = '' "
            + "OR (#{dueState} = 'EXPIRED' AND c.expire_date IS NOT NULL AND c.expire_date < CURDATE()) "
            + "OR (#{dueState} = 'SOON' AND c.expire_date IS NOT NULL "
            + "AND c.expire_date >= CURDATE() "
            + "AND c.expire_date <= DATE_ADD(CURDATE(), INTERVAL 30 DAY)) "
            + "OR (#{dueState} = 'NORMAL' AND (c.expire_date IS NULL "
            + "OR c.expire_date > DATE_ADD(CURDATE(), INTERVAL 30 DAY))))";

    @Select("SELECT c.id AS contractId, c.contract_no AS contractNo, c.customer_id AS customerId, "
            + "c.amount_cent AS amountCent, c.status AS status, c.expire_date AS validTo "
            + "FROM contract c "
            + "WHERE (#{status} IS NULL OR #{status} = '' OR c.status = #{status})"
            + DUE_STATE_WHERE
            + " ORDER BY c.expire_date IS NULL, c.expire_date ASC, c.id DESC")
    IPage<ContractListItemPO> selectContractPage(Page<ContractListItemPO> page,
                                                 @Param("status") String status,
                                                 @Param("dueState") String dueState);

    @Select("SELECT COUNT(*) FROM contract c "
            + "WHERE (#{status} IS NULL OR #{status} = '' OR c.status = #{status})"
            + DUE_STATE_WHERE)
    long countContract(@Param("status") String status, @Param("dueState") String dueState);
}
