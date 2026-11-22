package com.sent.sports.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.review.mapper.po.ProductReviewPO;
import com.sent.sports.review.mapper.po.ReviewRow;
import com.sent.sports.review.mapper.po.ReviewSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商品评价 Mapper（V55）。
 *
 * <p>两条约定：</p>
 * <ul>
 *   <li><b>防重靠索引</b>：{@code uk_review_order_product(order_no, product_id)} 是唯一防线，
 *       重复提交由 {@code DuplicateKeyException} 暴露，调用方转 40901，不做「先查后插」；</li>
 *   <li><b>昵称随行带出</b>：列表 LEFT JOIN {@code customer}，一次取全，避免逐条回查。</li>
 * </ul>
 */
@Mapper
public interface ReviewMapper extends BaseMapper<ProductReviewPO> {

    /**
     * 某商品的评价分页（按 id 倒序 = 最新在前）。
     * <p>排序不用 {@code created_at}：同一秒批量写入时时间相同，翻页会出现重复/漏行。</p>
     */
    @Select("SELECT r.id AS id, r.customer_id AS customerId, r.rating AS rating, "
            + "r.content AS content, r.images_json AS imagesJson, r.is_anonymous AS isAnonymous, "
            + "r.created_at AS createdAt, c.nickname AS nickname "
            + "FROM product_review r "
            + "LEFT JOIN customer c ON c.id = r.customer_id "
            + "WHERE r.product_id = #{productId} "
            + "ORDER BY r.id DESC")
    IPage<ReviewRow> selectByProductPage(Page<ReviewRow> page, @Param("productId") long productId);

    /**
     * 摘要聚合：总数 / 好评数 / 均分。
     * <p>{@code COALESCE} 兜住空集——商品还没人评价时 MySQL 会返回一行 (0, NULL, NULL)，
     * 不兜底会让前端拿到 null 好评率。</p>
     */
    @Select("SELECT COUNT(*) AS total, "
            + "COALESCE(SUM(CASE WHEN rating >= 4 THEN 1 ELSE 0 END), 0) AS positiveCount, "
            + "COALESCE(AVG(rating), 0) AS averageRating "
            + "FROM product_review WHERE product_id = #{productId}")
    ReviewSummaryRow selectSummary(@Param("productId") long productId);

    /**
     * 该订单是否已评价过此商品（资格查询用）。
     * <p>按 {@code order_no + product_id} 精确命中唯一索引，O(1)；
     * 不用「取列表再前端判断」——那样第一页没看到就以为没评过。</p>
     */
    @Select("SELECT COUNT(*) FROM product_review WHERE order_no = #{orderNo} AND product_id = #{productId}")
    int countByOrderAndProduct(@Param("orderNo") String orderNo, @Param("productId") long productId);
}
