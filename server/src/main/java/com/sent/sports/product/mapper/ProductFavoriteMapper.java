package com.sent.sports.product.mapper;

import com.sent.sports.product.mapper.po.ProductFavoriteRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品收藏 Mapper（2026-11-16）。
 *
 * <p>防重只靠唯一键 {@code uk_favorite_customer_product} + {@code INSERT IGNORE}，
 * 不用分布式锁：连点两下、双端同时点，结果都是「一条记录 + 影响行数 0/1」，
 * 影响行数不参与判断（0 也视为成功）。</p>
 * <p>列表与计数<b>必须同条件</b>（都只按 {@code customer_id} 过滤，不因下架而少算），
 * 否则会出现「显示 3 条、翻页只有 2 条」。</p>
 */
@Mapper
public interface ProductFavoriteMapper {

    /** 收藏（重复收藏影响行数 0，不报错）。 */
    @Insert("INSERT IGNORE INTO product_favorite (customer_id, product_id, created_by, created_at, updated_at) "
            + "VALUES (#{customerId}, #{productId}, #{customerId}, NOW(3), NOW(3))")
    int insertIgnore(@Param("customerId") long customerId, @Param("productId") long productId);

    /** 取消收藏（本来没收藏影响行数 0）。 */
    @Delete("DELETE FROM product_favorite WHERE customer_id = #{customerId} AND product_id = #{productId}")
    int deleteOne(@Param("customerId") long customerId, @Param("productId") long productId);

    /** 我的收藏分页：收藏时间倒序，联商品取标题/主图/最低在售价。 */
    @Select("SELECT p.id AS productId, p.title AS productName, p.main_image AS mainImage, "
            + "COALESCE((SELECT MIN(s.retail_price_cent) FROM sku s "
            + "          WHERE s.spu_id = p.id AND s.status = 'ON'), 0) AS priceCent, "
            + "p.status AS productStatus, p.deleted AS productDeleted, "
            + "f.created_at AS favoritedAt "
            + "FROM product_favorite f JOIN product p ON p.id = f.product_id "
            + "WHERE f.customer_id = #{customerId} "
            + "ORDER BY f.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ProductFavoriteRow> selectMyPage(@Param("customerId") long customerId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    /** 与 {@link #selectMyPage} 同条件的总数。 */
    @Select("SELECT COUNT(*) FROM product_favorite WHERE customer_id = #{customerId}")
    long countMy(@Param("customerId") long customerId);

    /** 某商品是否已被该客户收藏（1/0）。 */
    @Select("SELECT COUNT(*) FROM product_favorite "
            + "WHERE customer_id = #{customerId} AND product_id = #{productId}")
    int countByCustomerProduct(@Param("customerId") long customerId, @Param("productId") long productId);

    /**
     * 批量查「这批商品里哪些已收藏」。
     * 足迹列表要给每张卡片画心形，逐条查会 N+1；一次 IN 查询后由调用方做集合判断。
     */
    @Select("<script>"
            + "SELECT product_id FROM product_favorite WHERE customer_id = #{customerId} "
            + "AND product_id IN"
            + "<foreach collection=\"productIds\" item=\"pid\" open=\"(\" separator=\",\" close=\")\">#{pid}</foreach>"
            + "</script>")
    List<Long> selectFavoritedProductIds(@Param("customerId") long customerId,
                                         @Param("productIds") List<Long> productIds);
}
