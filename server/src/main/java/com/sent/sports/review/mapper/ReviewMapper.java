package com.sent.sports.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.review.mapper.po.AdminReviewRow;
import com.sent.sports.review.mapper.po.MyPendingReviewRow;
import com.sent.sports.review.mapper.po.MyReviewRow;
import com.sent.sports.review.mapper.po.MyReviewSummaryRow;
import com.sent.sports.review.mapper.po.ProductReviewPO;
import com.sent.sports.review.mapper.po.ReviewRow;
import com.sent.sports.review.mapper.po.ReviewSummaryRow;
import org.apache.ibatis.annotations.Delete;
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
     * 评价中心：我写过的评价分页。
     * <p>不按 {@code status} 过滤：被运营隐藏的评价也是「我写的」，
     * 列表要能看到并标记「已隐藏」，否则用户会以为评价凭空消失了。</p>
     * <p>商品信息 LEFT JOIN 带出（商品被物理删除时仍能出行，标题为空由 service 兜）。</p>
     * <p>2026-11-28 加排序：{@code sort} 由 service 归一成白名单常量再下推，
     * <b>不接受前端传裸 SQL</b>；三种口径分别是最新（id 倒序）、最早（id 正序）、
     * 综合（获赞多的在前，同赞数按时间倒序）。</p>
     */
    @Select({"<script>",
            "SELECT r.id AS id, r.product_id AS productId, p.title AS productTitle, ",
            "p.main_image AS mainImage, r.rating AS rating, r.content AS content, ",
            "r.images_json AS imagesJson, r.is_anonymous AS isAnonymous, r.status AS status, ",
            "r.view_count AS viewCount, r.like_count AS likeCount, r.created_at AS createdAt ",
            "FROM product_review r ",
            "LEFT JOIN product p ON p.id = r.product_id ",
            "WHERE r.customer_id = #{customerId} ",
            "<choose>",
            "<when test=\"sort == 'EARLIEST'\"> ORDER BY r.id ASC </when>",
            "<when test=\"sort == 'COMPREHENSIVE'\"> ORDER BY r.like_count DESC, r.id DESC </when>",
            "<otherwise> ORDER BY r.id DESC </otherwise>",
            "</choose>",
            "</script>"})
    IPage<MyReviewRow> selectMinePage(Page<MyReviewRow> page, @Param("customerId") long customerId,
                                      @Param("sort") String sort);

    /**
     * 评价中心「待评价」：已完成订单里、该商品尚未评价的行。
     * <p>「有没有评过」按 {@code order_no + product_id} 判断（与 {@code uk_review_order_product} 同口径），
     * 这样同一订单买了两个商品时，评过的那个就自动从待评价里消失。</p>
     * <p>商品 id 走 {@code sku.spu_id} 取：{@code order_item} 只存 sku_id，
     * 只按 sku 比对会让「买的是规格 A、评的是商品」永远对不上。</p>
     */
    @Select("SELECT o.order_no AS orderNo, p.id AS productId, "
            + "COALESCE(oi.spu_title_snap, p.title) AS productTitle, p.main_image AS mainImage, "
            + "oi.qty AS qty, (oi.amount_cent - oi.discount_alloc_cent) AS paidCent, "
            + "o.completed_at AS completedAt "
            + "FROM orders o "
            + "JOIN order_item oi ON oi.order_id = o.id "
            + "LEFT JOIN sku sk ON sk.id = oi.sku_id "
            + "LEFT JOIN product p ON p.id = sk.spu_id "
            + "WHERE o.customer_id = #{customerId} AND o.status = 'COMPLETED' "
            + "AND o.customer_deleted_at IS NULL "
            + "AND p.id IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM product_review r "
            + "WHERE r.order_no = o.order_no AND r.product_id = p.id) "
            + "ORDER BY o.completed_at DESC, oi.id DESC")
    IPage<MyPendingReviewRow> selectMyPendingPage(Page<MyPendingReviewRow> page,
                                                 @Param("customerId") long customerId);

    /**
     * 删除「我自己的」一条评价：SQL 带 {@code customer_id} 条件，越权删别人的永远影响 0 行。
     * <p>物理删除而不是软删：{@code uk_review_order_product} 会随之释放，
     * 用户删掉后可以重新评价同一订单商品（软删会让用户被唯一键卡住、又没有入口可改）。</p>
     */
    @Delete("DELETE FROM product_review WHERE id = #{id} AND customer_id = #{customerId}")
    int deleteMine(@Param("id") long id, @Param("customerId") long customerId);

    /**
     * 评价中心顶部统计：条数 / 浏览数合计 / 获赞数合计。
     * <p>{@code COALESCE} 兜住空集：没写过评价时 SUM 返回 NULL，不兜底前端会拿到 null 数字。</p>
     */
    @Select("SELECT COUNT(*) AS reviewCount, "
            + "COALESCE(SUM(view_count), 0) AS viewCount, "
            + "COALESCE(SUM(like_count), 0) AS likeCount "
            + "FROM product_review WHERE customer_id = #{customerId}")
    MyReviewSummaryRow selectMineSummary(@Param("customerId") long customerId);

    /**
     * 该订单是否已评价过此商品（资格查询用）。
     * <p>按 {@code order_no + product_id} 精确命中唯一索引，O(1)；
     * 不用「取列表再前端判断」——那样第一页没看到就以为没评过。</p>
     * <p>刻意不带 status 条件：隐藏只是不展示，不代表没评过，否则运营一隐藏就能再刷一条。</p>
     */
    @Select("SELECT COUNT(*) FROM product_review WHERE order_no = #{orderNo} AND product_id = #{productId}")
    int countByOrderAndProduct(@Param("orderNo") String orderNo, @Param("productId") long productId);
}
