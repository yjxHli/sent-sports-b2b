package com.sent.sports.report.mapper;

import com.sent.sports.report.mapper.po.SalesCountRow;
import com.sent.sports.report.mapper.po.SalesRankRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售业绩排行 + 工作台看板聚合 Mapper。
 *
 * <p><b>口径</b>：成交额一律以 {@code paid_at} 为时间基准、{@code SUM(paid_cent)} 聚合，
 * 只计 {@code pay_status IN ('PAID','PART_REFUNDED')}；与销售趋势、财务台账保持同一把尺子。
 * 时间窗左闭右开 {@code [from, to)}，全部参数化，ORDER BY 固定，防注入。</p>
 *
 * <p><b>ArchUnit 规则 4</b>：位于 {@code report.mapper}，SQL 中的跨模块表名只是字符串，
 * 不引用 order / stock / chat 等模块的任何 Java 类型，与 {@code SalesTrendMapper} 的既有做法一致。</p>
 */
@Mapper
public interface DashboardReportMapper {

    /**
     * 按归属销售（{@code orders.owner_staff_id}）聚合近 N 天业绩。
     * <p>未分配归属的订单合并成一行「未分配」，保证排行金额合计等于同区间销售趋势总额。</p>
     */
    @Select("SELECT o.owner_staff_id AS staffId, COALESCE(s.name, '未分配') AS staffName, "
            + "COUNT(DISTINCT o.customer_id) AS customerCount, COUNT(*) AS orderCount, "
            + "SUM(o.paid_cent) AS amountCent "
            + "FROM orders o LEFT JOIN sys_staff s ON s.id = o.owner_staff_id "
            + "WHERE o.pay_status IN ('PAID', 'PART_REFUNDED') "
            + "AND o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "GROUP BY o.owner_staff_id, s.name "
            + "ORDER BY amountCent DESC, o.owner_staff_id")
    List<SalesRankRow> selectSalesRank(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    /** 指定时间窗的成交汇总（今日订单数 / 今日实付金额）。 */
    @Select("SELECT COUNT(*) AS orderCount, COALESCE(SUM(paid_cent), 0) AS amountCent "
            + "FROM orders WHERE pay_status IN ('PAID', 'PART_REFUNDED') "
            + "AND paid_at >= #{from} AND paid_at < #{to}")
    SalesCountRow selectSales(@Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);

    /** 待发货：WAIT_SHIP / PART_SHIPPED（与发货工作台的可发货状态一致）。 */
    @Select("SELECT COUNT(*) FROM orders WHERE status IN ('WAIT_SHIP', 'PART_SHIPPED')")
    long countPendingShip();

    /** 待审核定制：审核台 pendingList 的同一状态。 */
    @Select("SELECT COUNT(*) FROM custom_order WHERE status = 'WAIT_REVIEW'")
    long countPendingCustomReview();

    /** 待接入会话：客服工作台排队队列的同一状态。 */
    @Select("SELECT COUNT(*) FROM chat_session WHERE status = 'WAITING'")
    long countWaitingChat();

    /** 预警 SKU：可售 = max(0, 实物 - 锁定 - 待发 - 冻结)，低于等于安全库存即预警。 */
    @Select("SELECT COUNT(*) FROM sku_stock "
            + "WHERE GREATEST(physical_qty - locked_qty - pending_qty - frozen_qty, 0) "
            + "<= GREATEST(safety_qty, 0)")
    long countWarningSku();

    /** 待审核退款：仅 PENDING 可操作。 */
    @Select("SELECT COUNT(*) FROM refund_order WHERE status = 'PENDING'")
    long countPendingRefund();

    /** 逾期应收：由财务台账维护的 OVERDUE 状态，不在报表侧另算一套口径。 */
    @Select("SELECT COUNT(*) FROM receivable WHERE status = 'OVERDUE'")
    long countOverdueReceivable();
}
