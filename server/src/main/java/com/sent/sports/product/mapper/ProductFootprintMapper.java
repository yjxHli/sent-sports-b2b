package com.sent.sports.product.mapper;

import com.sent.sports.product.mapper.po.ProductFootprintRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品浏览足迹 Mapper（2026-11-16）。
 *
 * <p>写入走 {@code ON DUPLICATE KEY UPDATE}（唯一键 {@code uk_footprint_customer_product}）：
 * 同一商品反复浏览只刷新 {@code last_viewed_at} 并 +1 次数，不新增行。
 * 这是淘宝式足迹的关键——按商品去重、按最近浏览倒序。</p>
 */
@Mapper
public interface ProductFootprintMapper {

    /** 记录一次浏览（重复浏览刷新时间并累加次数）。 */
    @Insert("INSERT INTO product_footprint "
            + "(customer_id, product_id, view_count, last_viewed_at, created_by, created_at, updated_at) "
            + "VALUES (#{customerId}, #{productId}, 1, NOW(3), #{customerId}, NOW(3), NOW(3)) "
            + "ON DUPLICATE KEY UPDATE view_count = view_count + 1, last_viewed_at = NOW(3), updated_at = NOW(3)")
    int upsert(@Param("customerId") long customerId, @Param("productId") long productId);

    /**
     * 我的足迹分页：最近浏览时间倒序。
     * <p>2026-11-28：只返回 {@code cutoff} 之后的记录（服务端留一个月，默认 30 天）——
     * 过滤写在 SQL 里而不是查完再筛，否则分页 total 会把已过期的记录也算进去。</p>
     */
    @Select("SELECT p.id AS productId, p.title AS productName, p.main_image AS mainImage, "
            + "COALESCE((SELECT MIN(s.retail_price_cent) FROM sku s "
            + "          WHERE s.spu_id = p.id AND s.status = 'ON'), 0) AS priceCent, "
            + "p.status AS productStatus, p.deleted AS productDeleted, "
            + "fp.view_count AS viewCount, fp.last_viewed_at AS lastViewedAt "
            + "FROM product_footprint fp JOIN product p ON p.id = fp.product_id "
            + "WHERE fp.customer_id = #{customerId} AND fp.last_viewed_at >= #{cutoff} "
            + "ORDER BY fp.last_viewed_at DESC, fp.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ProductFootprintRow> selectMyPage(@Param("customerId") long customerId,
                                           @Param("cutoff") LocalDateTime cutoff,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 与 {@link #selectMyPage} 同条件的总数（同样只算未过期的一个月）。 */
    @Select("SELECT COUNT(*) FROM product_footprint "
            + "WHERE customer_id = #{customerId} AND last_viewed_at >= #{cutoff}")
    long countMy(@Param("customerId") long customerId, @Param("cutoff") LocalDateTime cutoff);

    /** 清空我的足迹。 */
    @Delete("DELETE FROM product_footprint WHERE customer_id = #{customerId}")
    int deleteAllMy(@Param("customerId") long customerId);

    /**
     * 清理过期足迹（2026-11-28 用户要求：足迹只保留一个月）。
     * <p>按 {@code last_viewed_at < cutoff} 删：一次性删全表，不区分客户——
     * 过期记录对任何客户都不再有意义。返回删除行数用于日志。</p>
     */
    @Delete("DELETE FROM product_footprint WHERE last_viewed_at < #{cutoff}")
    int deleteViewedBefore(@Param("cutoff") LocalDateTime cutoff);
}
