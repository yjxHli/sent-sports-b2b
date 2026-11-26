package com.sent.sports.report.mapper;

import com.sent.sports.common.anno.DataScope;
import com.sent.sports.report.mapper.po.CustomerMixRow;
import com.sent.sports.report.mapper.po.ProductDetailRow;
import com.sent.sports.report.mapper.po.RepeatCustomerRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拓展报表聚合 Mapper（2026-11-23）。
 *
 * <p>三条查询统一口径：{@code orders.pay_status IN ('PAID','PART_REFUNDED')}、
 * {@code paid_at} 左闭右开窗口；数据范围由 {@link DataScope}(alias="o") 下推，
 * 主管看团队、财务/超管看全部，与订单列表同一套机制。</p>
 *
 * <p>复购判定要追到「窗口之前是否已有成交」，所以扫描范围到 {@code to} 为止（含历史），
 * 但只用条件聚合返回窗口内的订单数与金额，再由服务层按首单时间分新老客。</p>
 */
@Mapper
public interface ReportExtensionMapper {

    /** 客户类型分组；customer_type 为 null / 空的原始行照实返回，由服务层归一为 UNKNOWN。 */
    @DataScope(alias = "o")
    @Select("SELECT o.customer_type AS customerType, "
            + "COUNT(DISTINCT o.customer_id) AS customerCount, "
            + "COUNT(DISTINCT o.id) AS orderCount, "
            + "COALESCE(SUM(o.paid_cent), 0) AS amountCent "
            + "FROM orders o "
            + "WHERE o.pay_status IN ('PAID', 'PART_REFUNDED') "
            + "AND o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "GROUP BY o.customer_type")
    List<CustomerMixRow> selectCustomerMix(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /** 商品维度聚合：成交额、成本、可用库存、价格来源一次取齐，按金额降序返回全量。 */
    @DataScope(alias = "o")
    @Select("SELECT oi.sku_id AS skuId, "
            + "MAX(COALESCE(NULLIF(oi.spu_title_snap, ''), p.title, CONCAT('商品#', oi.sku_id))) AS skuName, "
            + "MAX(oi.spec_snap) AS spec, "
            + "COALESCE(SUM(oi.qty), 0) AS qty, "
            + "COALESCE(SUM(oi.amount_cent), 0) AS amountCent, "
            + "COALESCE(MAX(k.cost_price_cent), 0) AS costPriceCent, "
            + "GROUP_CONCAT(DISTINCT oi.price_source ORDER BY oi.price_source SEPARATOR ',') AS priceSources, "
            + "COALESCE(MAX(s.physical_qty - s.locked_qty - s.pending_qty), 0) AS stockQty, "
            + "COUNT(DISTINCT o.id) AS orderCount "
            + "FROM order_item oi "
            + "JOIN orders o ON o.id = oi.order_id "
            + "LEFT JOIN sku k ON k.id = oi.sku_id "
            + "LEFT JOIN product p ON p.id = k.spu_id "
            + "LEFT JOIN sku_stock s ON s.sku_id = oi.sku_id "
            + "WHERE o.pay_status IN ('PAID', 'PART_REFUNDED') "
            + "AND o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "GROUP BY oi.sku_id "
            + "ORDER BY amountCent DESC, oi.sku_id")
    List<ProductDetailRow> selectProductDetails(@Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    /** 每个有窗口内成交的客户一行：窗口内订单数/金额 + 历史首单时间。 */
    @DataScope(alias = "o")
    @Select("SELECT o.customer_id AS customerId, "
            + "SUM(CASE WHEN o.paid_at >= #{from} AND o.paid_at < #{to} THEN 1 ELSE 0 END) AS periodOrderCount, "
            + "COALESCE(SUM(CASE WHEN o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "THEN o.paid_cent ELSE 0 END), 0) AS periodAmountCent, "
            + "MIN(o.paid_at) AS firstPaidAt, "
            + "COUNT(*) AS totalOrderCount "
            + "FROM orders o "
            + "WHERE o.pay_status IN ('PAID', 'PART_REFUNDED') AND o.paid_at < #{to} "
            + "GROUP BY o.customer_id "
            + "HAVING SUM(CASE WHEN o.paid_at >= #{from} AND o.paid_at < #{to} THEN 1 ELSE 0 END) > 0")
    List<RepeatCustomerRow> selectRepeatCustomers(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);
}
