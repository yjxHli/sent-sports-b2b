package com.sent.sports.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.finance.mapper.po.ContractListItemPO;
import com.sent.sports.finance.mapper.po.ContractPO;
import com.sent.sports.finance.mapper.po.ContractRemindRow;
import com.sent.sports.finance.mapper.po.MyContractRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * 关键字片段：合同 ID / 合同号 / 客户 ID 三选一模糊匹配。
     * <p>ID 是 BIGINT，转成字符串再 LIKE，运营可以直接贴单号或 ID 定位，
     * 不必知道自己在查哪一列。合同表只有几十行，CAST 造成的索引失效可以接受。</p>
     */
    String KEYWORD_WHERE = " AND (#{keyword} IS NULL OR #{keyword} = '' "
            + "OR c.contract_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR CAST(c.id AS CHAR) LIKE CONCAT('%', #{keyword}, '%') "
            + "OR CAST(c.customer_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%'))";

    @Select("SELECT c.id AS contractId, c.contract_no AS contractNo, c.customer_id AS customerId, "
            + "c.amount_cent AS amountCent, c.status AS status, c.expire_date AS validTo "
            + "FROM contract c "
            + "WHERE c.deleted = 0 AND (#{status} IS NULL OR #{status} = '' OR c.status = #{status})"
            + DUE_STATE_WHERE
            + KEYWORD_WHERE
            + " ORDER BY c.expire_date IS NULL, c.expire_date ASC, c.id DESC")
    IPage<ContractListItemPO> selectContractPage(Page<ContractListItemPO> page,
                                                 @Param("status") String status,
                                                 @Param("dueState") String dueState,
                                                 @Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM contract c "
            + "WHERE c.deleted = 0 AND (#{status} IS NULL OR #{status} = '' OR c.status = #{status})"
            + DUE_STATE_WHERE
            + KEYWORD_WHERE)
    long countContract(@Param("status") String status, @Param("dueState") String dueState,
                       @Param("keyword") String keyword);

    /**
     * 管理端批量软删（V34）：按合同号打 deleted 标记；合同金额与到期日不改写。
     */
    @Update("<script>UPDATE contract SET deleted = 1 WHERE deleted = 0 AND "
            + "(contract_no IN "
            + "<foreach collection='nos' item='no' open='(' separator=',' close=')'>#{no}</foreach>"
            + " OR CAST(id AS CHAR) IN "
            + "<foreach collection='nos' item='no' open='(' separator=',' close=')'>#{no}</foreach>)"
            + "</script>")
    int batchMarkDeleted(@Param("nos") List<String> nos);

    /** 30 天内到期的未删除合同 + 当前提醒档位（V67）；档位计算与过滤留给 finance service。 */
    @Select("SELECT c.id AS contractId, c.customer_id AS customerId, c.contract_no AS contractNo, "
            + "COALESCE(c.amount_cent, 0) AS amountCent, c.expire_date AS expireDate, "
            + "c.expire_remind_level AS remindLevel "
            + "FROM contract c WHERE c.deleted = 0 AND c.expire_date IS NOT NULL "
            + "AND DATEDIFF(c.expire_date, #{today}) BETWEEN 0 AND 30 "
            + "ORDER BY c.expire_date, c.id")
    List<ContractRemindRow> selectExpiring(@Param("today") LocalDate today);

    /**
     * 条件抢占：只有当前档位低于目标档位才写入，返回 0 表示并发下已被别的任务提醒过。
     * 时间列只做留痕，不参与条件，避免时钟回拨导致重复提醒。
     */
    @Update("UPDATE contract SET expire_remind_level = #{level}, expire_reminded_at = NOW(3) "
            + "WHERE id = #{contractId} AND expire_remind_level < #{level}")
    int updateRemindLevel(@Param("contractId") long contractId, @Param("level") int level);

    /**
     * 用户端「我的合同」：按客户收口，软删合同不展示；新签的排前面。
     * <p>只取展示需要的列，合同文件地址不在此投影内（演示地址多为占位值）。</p>
     */
    @Select("SELECT c.id AS contractId, c.contract_no AS contractNo, "
            + "COALESCE(c.amount_cent, 0) AS amountCent, c.status AS status, "
            + "c.sign_date AS signDate, c.expire_date AS expireDate, c.discount_rate AS discountRate "
            + "FROM contract c WHERE c.customer_id = #{customerId} AND c.deleted = 0 "
            + "ORDER BY c.sign_date IS NULL, c.sign_date DESC, c.id DESC")
    List<MyContractRow> selectMyContracts(@Param("customerId") long customerId);
}
