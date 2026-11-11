package com.sent.sports.report.mapper;

import com.sent.sports.report.mapper.po.SalesTrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售趋势 Mapper（批 4）：按已支付口径聚合 {@code orders} 表。
 *
 * <p><b>口径（用户确认）</b>：时间基准为 {@code paid_at}，聚合字段严格为 {@code paid_cent}；
 * 只计 {@code pay_status IN ('PAID','PART_REFUNDED')}，全额退款 {@code FULL_REFUNDED}
 * 以及未支付 {@code UNPAID/PAYING} 整单剔除，不计入销售业绩。</p>
 *
 * <p><b>ArchUnit 规则 4</b>：本类位于 {@code report.mapper}（不在 controller/service/event 约束范围），
 * 且 SQL 中的表名只是字符串，不引用 order 模块的任何 Java 类型 —— 与
 * {@code InvoiceMapper.insertFromOrder} 的既有做法一致，不构成跨模块类依赖。</p>
 *
 * <p>时间窗口为左闭右开 {@code [from, to)}，全部参数化；{@code ORDER BY} 固定，防注入。</p>
 */
@Mapper
public interface SalesTrendMapper {

    @Select("SELECT DATE_FORMAT(paid_at, '%Y-%m-%d') AS day, "
            + "SUM(paid_cent) AS amountCent, COUNT(*) AS orderCount "
            + "FROM orders "
            + "WHERE pay_status IN ('PAID', 'PART_REFUNDED') "
            + "AND paid_at >= #{from} AND paid_at < #{to} "
            + "GROUP BY DATE_FORMAT(paid_at, '%Y-%m-%d') "
            + "ORDER BY day")
    List<SalesTrendRow> selectDailyTrend(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);
}
