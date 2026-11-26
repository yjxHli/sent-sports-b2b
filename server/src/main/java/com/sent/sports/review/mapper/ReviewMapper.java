package com.sent.sports.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.review.mapper.po.AdminReviewRow;
import com.sent.sports.review.mapper.po.ProductReviewPO;
import com.sent.sports.review.mapper.po.ReviewRow;
import com.sent.sports.review.mapper.po.ReviewSummaryRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * <p>V56：只取 {@code status='NORMAL'} 的评价——被运营隐藏的违规内容不能出现在用户端，
     * 总数与摘要（另一个聚合查询）用同一条件，否则「列表 2 条、总数 3 条」对不上。</p>
     */
    @Select("SELECT r.id AS id, r.customer_id AS customerId, r.rating AS rating, "
            + "r.content AS content, r.images_json AS imagesJson, r.is_anonymous AS isAnonymous, "
            + "r.created_at AS createdAt, c.nickname AS nickname "
            + "FROM product_review r "
            + "LEFT JOIN customer c ON c.id = r.customer_id "
            + "WHERE r.product_id = #{productId} AND r.status = 'NORMAL' "
            + "ORDER BY r.id DESC")
    IPage<ReviewRow> selectByProductPage(Page<ReviewRow> page, @Param("productId") long productId);

    /**
     * 摘要聚合：总数 / 好评数（≥4 星）/ 差评数（≤2 星）/ 均分。
     * <p>{@code COALESCE} 兜住空集——商品还没人评价时 MySQL 会返回一行 (0, NULL, NULL)，
     * 不兜底会让前端拿到 null 好评率。</p>
     * <p>差评与好评分开统计而不是「总数 - 好评」：3 星中评既不算好评也不算差评，
     * 用减法会把中评错算成差评，口碑概览直接失真。</p>
     * <p>V56：隐藏评价不参与统计，好评率按「可见评价」重算。</p>
     */
    @Select("SELECT COUNT(*) AS total, "
            + "COALESCE(SUM(CASE WHEN rating >= 4 THEN 1 ELSE 0 END), 0) AS positiveCount, "
            + "COALESCE(SUM(CASE WHEN rating <= 2 THEN 1 ELSE 0 END), 0) AS negativeCount, "
            + "COALESCE(AVG(rating), 0) AS averageRating "
            + "FROM product_review WHERE product_id = #{productId} AND status = 'NORMAL'")
    ReviewSummaryRow selectSummary(@Param("productId") long productId);

    /**
     * 管理端分页：不按状态强制过滤（默认看全部，运营才能找回被隐藏的那条并恢复）。
     * <p>动态条件写 {@code <script>}；三段筛选都允许为空，空白由 service 归一成 null。</p>
     */
    @Select({"<script>",
            "SELECT r.id AS reviewId, r.product_id AS productId, p.title AS productTitle, "
                    + "r.rating AS rating, r.content AS content, r.images_json AS imagesJson, "
                    + "r.is_anonymous AS isAnonymous, c.nickname AS nickname, r.status AS status, "
                    + "r.created_at AS createdAt ",
            "FROM product_review r ",
            "LEFT JOIN product p ON p.id = r.product_id ",
            "LEFT JOIN customer c ON c.id = r.customer_id ",
            "WHERE 1 = 1 ",
            "<if test='productId != null'> AND r.product_id = #{productId} </if>",
            "<if test='rating != null'> AND r.rating = #{rating} </if>",
            "<if test='status != null'> AND r.status = #{status} </if>",
            "ORDER BY r.id DESC",
            "</script>"})
    IPage<AdminReviewRow> selectAdminPage(Page<AdminReviewRow> page,
                                          @Param("productId") Long productId,
                                          @Param("rating") Integer rating,
                                          @Param("status") String status);

    /** 单条（含状态与商品标题）：状态变更前后要对比，并在响应里回显商品信息。 */
    @Select("SELECT r.id AS reviewId, r.product_id AS productId, p.title AS productTitle, "
            + "r.rating AS rating, r.content AS content, r.images_json AS imagesJson, "
            + "r.is_anonymous AS isAnonymous, c.nickname AS nickname, r.status AS status, "
            + "r.created_at AS createdAt "
            + "FROM product_review r "
            + "LEFT JOIN product p ON p.id = r.product_id "
            + "LEFT JOIN customer c ON c.id = r.customer_id "
            + "WHERE r.id = #{id}")
    AdminReviewRow selectAdminById(@Param("id") long id);

    /** 只改状态；{@code updated_by} 无此列，审计靠 sys_operation_log。 */
    @Update("UPDATE product_review SET status = #{status}, updated_at = NOW(3) WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status, @Param("operatorId") Long operatorId);

    /**
     * 写入操作日志（{@code sys_operation_log}）。
     * <p>评价模块与 system 模块无 Bean 依赖，这里按「共享审计表」直接落库，
     * 与 {@code CustomerMapper.insertOperationLog} 同一口径；before/after 用 JSON 对象记录状态。</p>
     */
    @Insert("INSERT INTO sys_operation_log "
            + "(staff_id, module, action, target_type, target_id, reason, before_json, after_json, created_by) "
            + "VALUES (#{staffId}, 'REVIEW', #{action}, 'PRODUCT_REVIEW', #{targetId}, #{reason}, "
            + "JSON_OBJECT('status', #{beforeStatus}), #{afterJson}, #{operatorId})")
    int insertStatusLog(@Param("staffId") Long staffId,
                        @Param("action") String action,
                        @Param("targetId") String targetId,
                        @Param("beforeStatus") String beforeStatus,
                        @Param("afterJson") String afterJson,
                        @Param("reason") String reason,
                        @Param("operatorId") Long operatorId);

    /**
     * 该订单是否已评价过此商品（资格查询用）。
     * <p>按 {@code order_no + product_id} 精确命中唯一索引，O(1)；
     * 不用「取列表再前端判断」——那样第一页没看到就以为没评过。</p>
     * <p>刻意不带 status 条件：隐藏只是不展示，不代表没评过，否则运营一隐藏就能再刷一条。</p>
     */
    @Select("SELECT COUNT(*) FROM product_review WHERE order_no = #{orderNo} AND product_id = #{productId}")
    int countByOrderAndProduct(@Param("orderNo") String orderNo, @Param("productId") long productId);
}
