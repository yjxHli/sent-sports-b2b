package com.sent.sports.report.mapper;

import com.sent.sports.common.anno.DataScope;
import com.sent.sports.report.mapper.po.TopCustomerRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大客户采购排行 Mapper（2026-11-21）。
 *
 * <p><b>口径</b>：{@code orders.paid_at} 时间窗、{@code SUM(paid_cent)}、只计 PAID / PART_REFUNDED，
 * 按 {@code orders.customer_id} 聚合。</p>
 *
 * <p><b>数据权限</b>：与热销商品同一套机制 —— {@code @DataScope(alias = "o")} 把
 * {@code o.owner_staff_id = self / IN (team)} 注入最外层 WHERE，因此主管只看到团队订单带来的客户。</p>
 *
 * <p>返回全量聚合行（按金额降序），Top N 截断与占比在 Service 完成。</p>
 */
@Mapper
public interface TopCustomerMapper {

    @DataScope(alias = "o")
    @Select("SELECT o.customer_id AS customerId, "
            + "COALESCE(NULLIF(ep.company_name, ''), NULLIF(c.nickname, ''), "
            + "CONCAT('客户#', o.customer_id)) AS customerName, "
            + "MAX(c.phone) AS phone, "
            + "COUNT(*) AS orderCount, "
            + "COALESCE(SUM(o.paid_cent), 0) AS amountCent "
            + "FROM orders o "
            + "LEFT JOIN customer c ON c.id = o.customer_id "
            + "LEFT JOIN enterprise_profile ep ON ep.customer_id = o.customer_id "
            + "WHERE o.pay_status IN ('PAID', 'PART_REFUNDED') "
            + "AND o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "GROUP BY o.customer_id, ep.company_name, c.nickname "
            + "ORDER BY amountCent DESC, o.customer_id")
    List<TopCustomerRow> selectTopCustomers(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);
}
