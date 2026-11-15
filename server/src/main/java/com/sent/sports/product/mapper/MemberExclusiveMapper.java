package com.sent.sports.product.mapper;

import com.sent.sports.product.mapper.po.ExclusiveProductRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 会员专属商品 Mapper（2026-11-16）。
 *
 * <p>两个来源各一条 SQL，由 {@code MemberExclusiveServiceImpl} 合并去重：</p>
 * <ul>
 *   <li>{@link #selectAgreementProducts}：一客一价（{@code customer_sku_price}），
 *       只取在售 SKU 与在售 SPU，有效期为空视为不限期；</li>
 *   <li>{@link #selectLevelProducts}：商品配了 {@code min_visible_level} 的候选集，
 *       具体是否够等级由 Java 侧比较（等级序是业务概念，不塞进 SQL 做字符串比较）。</li>
 * </ul>
 */
@Mapper
public interface MemberExclusiveMapper {

    /** 该客户有专属价（一客一价）的在售商品，价格取最低专属价。 */
    @Select("SELECT p.id AS productId, p.title AS productName, p.main_image AS mainImage, "
            + "MIN(csp.price_cent) AS priceCent, 'AGREEMENT' AS source "
            + "FROM customer_sku_price csp "
            + "JOIN sku s ON s.id = csp.sku_id AND s.status = 'ON' "
            + "JOIN product p ON p.id = s.spu_id AND p.deleted = 0 AND p.status = 'ON_SHELF' "
            + "WHERE csp.customer_id = #{customerId} "
            + "AND (csp.valid_from IS NULL OR csp.valid_from <= NOW(3)) "
            + "AND (csp.valid_to IS NULL OR csp.valid_to >= NOW(3)) "
            + "GROUP BY p.id, p.title, p.main_image "
            + "ORDER BY p.id")
    List<ExclusiveProductRow> selectAgreementProducts(@Param("customerId") long customerId);

    /** 配了等级门槛的在售商品（价格取最低在售 SKU 零售价），是否够等级由调用方判断。 */
    @Select("SELECT p.id AS productId, p.title AS productName, p.main_image AS mainImage, "
            + "p.min_visible_level AS minLevel, 'LEVEL' AS source, "
            + "COALESCE((SELECT MIN(s.retail_price_cent) FROM sku s "
            + "          WHERE s.spu_id = p.id AND s.status = 'ON'), 0) AS priceCent "
            + "FROM product p "
            + "WHERE p.deleted = 0 AND p.status = 'ON_SHELF' AND p.min_visible_level IS NOT NULL "
            + "ORDER BY p.id")
    List<ExclusiveProductRow> selectLevelProducts();
}
