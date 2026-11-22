package com.sent.sports.report.mapper;

import com.sent.sports.common.anno.DataScope;
import com.sent.sports.report.mapper.po.HotProductRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 热销商品聚合 Mapper（2026-11-21）。
 *
 * <p><b>口径</b>：与销售趋势 / 财务台账同一把尺子 —— {@code orders.paid_at} 时间窗左闭右开，
 * 只计 {@code pay_status IN ('PAID','PART_REFUNDED')}，全额退款整单剔除；
 * 商品维度按 {@code order_item.sku_id} 聚合 {@code SUM(qty)} 与 {@code SUM(amount_cent)}。</p>
 *
 * <p><b>数据权限</b>：标注 {@link DataScope}（别名 {@code o}），由 MyBatis 拦截器把
 * {@code o.owner_staff_id = self / IN (team)} 注入最外层 WHERE —— 主管看团队、财务/超管看全部，
 * 与订单列表同一套机制，不在 Service 里另写一份过滤逻辑。</p>
 *
 * <p>返回<b>全量</b>聚合行（按金额降序），Top N 截断与占比在 Service 内完成：
 * SKU 基数有限（在架 + 历史 SKU 数百），一次聚合回传后再截断，比"查 Top N + 另查总额"两条 SQL 更简单。</p>
 */
@Mapper
public interface HotProductMapper {

    @DataScope(alias = "o")
    @Select("SELECT oi.sku_id AS skuId, "
            + "MAX(COALESCE(NULLIF(oi.spu_title_snap, ''), p.title, CONCAT('商品#', oi.sku_id))) AS skuName, "
            + "MAX(oi.spec_snap) AS spec, "
            + "COALESCE(SUM(oi.qty), 0) AS qty, "
            + "COALESCE(SUM(oi.amount_cent), 0) AS amountCent, "
            + "COUNT(DISTINCT o.id) AS orderCount "
            + "FROM order_item oi "
            + "JOIN orders o ON o.id = oi.order_id "
            + "LEFT JOIN sku k ON k.id = oi.sku_id "
            + "LEFT JOIN product p ON p.id = k.spu_id "
            + "WHERE o.pay_status IN ('PAID', 'PART_REFUNDED') "
            + "AND o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "GROUP BY oi.sku_id "
            + "ORDER BY amountCent DESC, oi.sku_id")
    List<HotProductRow> selectHotProducts(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);
}
