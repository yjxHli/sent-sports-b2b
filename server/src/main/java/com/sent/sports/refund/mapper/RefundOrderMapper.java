package com.sent.sports.refund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.refund.mapper.po.RefundAuditDetailPO;
import com.sent.sports.refund.mapper.po.RefundAuditItemPO;
import com.sent.sports.refund.mapper.po.RefundDetailPO;
import com.sent.sports.refund.mapper.po.RefundListPO;
import com.sent.sports.refund.mapper.po.RefundOrderLinePO;
import com.sent.sports.refund.mapper.po.RefundOrderPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款 Mapper（P5-15）。
 * <p>超额退款（{@code REFUND_OVERFLOW(40940)}）不靠应用层累加，而是每次申请前用 {@link #sumInFlightAmount}
 * 汇总「在途 + 已退」金额，与订单实付比较；同一订单的并发申请由 {@code SELECT ... FOR UPDATE} 串行化。
 */
@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrderPO> {

    /** 按退款单号读取（前台与后台共用入口），联查订单号供回调用。 */
    @Select("SELECT r.*, o.order_no AS orderNo FROM refund_order r "
            + "LEFT JOIN orders o ON o.id = r.order_id WHERE r.refund_no = #{refundNo}")
    RefundOrderPO selectByRefundNo(@Param("refundNo") String refundNo);

    /**
     * 管理端关单的全额退款单：直接落 REFUNDED 终态（模拟网关已同步受理），
     * 原因码 ADMIN_CLOSE、备注写关单原因，避免再走一遍「申请 → 审核」人工链路。
     * {@code uk_refund_no} 保证单号唯一，重复关单在订单条件迁移处已被挡住。
     */
    @Insert("INSERT INTO refund_order (refund_no, order_id, type, amount_cent, point_refund, status, "
            + "reason_code, remark, applicant_type, refunded_at, created_at, updated_at) "
            + "VALUES (#{refundNo}, #{orderId}, 'ONLY_REFUND', #{amountCent}, 0, 'REFUNDED', "
            + "'ADMIN_CLOSE', #{reason}, 'STAFF', NOW(3), NOW(3), NOW(3))")
    int insertCloseRefund(@Param("refundNo") String refundNo,
                          @Param("orderId") long orderId,
                          @Param("amountCent") long amountCent,
                          @Param("reason") String reason);

    /**
     * 订单维度「在途 + 已退」金额合计：PENDING / REFUNDING / REFUNDED 均计入占用，
     * 只有 FAILED / CLOSED 释放额度。
     */
    @Select("SELECT COALESCE(SUM(amount_cent), 0) FROM refund_order "
            + "WHERE order_id = #{orderId} AND status IN ('PENDING', 'REFUNDING', 'REFUNDED')")
    long sumInFlightAmount(@Param("orderId") long orderId);

    /** 加行锁读取订单全部退款单，用于串行化同一订单的并发退款申请。 */
    @Select("SELECT * FROM refund_order WHERE order_id = #{orderId} FOR UPDATE")
    List<RefundOrderPO> selectByOrderIdForUpdate(@Param("orderId") long orderId);

    /** 待审核退款（后台列表按申请时间升序，先到先审）。 */
    @Select("SELECT * FROM refund_order WHERE status = 'PENDING' ORDER BY created_at, id LIMIT #{limit}")
    List<RefundOrderPO> selectPending(@Param("limit") int limit);

    /**
     * 管理端退款审核列表（收口阶段）：左联 orders 取订单号，状态为空时返回全部。
     * <p>关键字同时匹配退款单号与订单号：客服拿到的往往只有其中一种号，
     * 让他们先判断「这是哪个号」再选输入框没有意义。</p>
     * 由 MyBatis-Plus {@link Page} 追加 LIMIT/OFFSET，总数另由 {@link #countAudit} 统计。
     */
    @Select("SELECT r.refund_no AS refundNo, o.order_no AS orderNo, r.status AS status, "
            + "r.amount_cent AS amountCent, "
            + "COALESCE(r.audit_remark, r.remark, r.reason_code) AS reason, "
            + "r.images_json AS imagesJson, r.created_at AS createdAt "
            + "FROM refund_order r LEFT JOIN orders o ON o.id = r.order_id "
            + "WHERE r.deleted = 0 AND (#{status} IS NULL OR #{status} = '' OR r.status = #{status}) "
            + "AND (#{keyword} IS NULL OR #{keyword} = '' "
            + "     OR r.refund_no LIKE CONCAT('%', #{keyword}, '%') "
            + "     OR o.order_no LIKE CONCAT('%', #{keyword}, '%')) "
            + "ORDER BY r.created_at DESC, r.id DESC")
    IPage<RefundAuditItemPO> selectAuditPage(Page<RefundAuditItemPO> page,
                                             @Param("status") String status,
                                             @Param("keyword") String keyword);

    /** 管理端退款审核列表总数（与 selectAuditPage 同一过滤条件，含关键字联查）。 */
    @Select("SELECT COUNT(*) FROM refund_order r "
            + "LEFT JOIN orders o ON o.id = r.order_id "
            + "WHERE r.deleted = 0 AND (#{status} IS NULL OR #{status} = '' OR r.status = #{status}) "
            + "AND (#{keyword} IS NULL OR #{keyword} = '' "
            + "     OR r.refund_no LIKE CONCAT('%', #{keyword}, '%') "
            + "     OR o.order_no LIKE CONCAT('%', #{keyword}, '%'))")
    long countAudit(@Param("status") String status, @Param("keyword") String keyword);

    /**
     * 管理端批量软删（V34）：只写 deleted 标记，退款金额 / 支付流水 / 应收一律不碰。
     * 已删行重复删除返回 0，按幂等成功处理。
     */
    @Update("<script>UPDATE refund_order SET deleted = 1 WHERE deleted = 0 AND refund_no IN "
            + "<foreach collection='nos' item='no' open='(' separator=',' close=')'>#{no}</foreach>"
            + "</script>")
    int batchMarkDeleted(@Param("nos") List<String> nos);

    /** 条件迁移：返回 0 行表示状态已被并发推进。 */
    @Update("UPDATE refund_order SET status = #{to} WHERE refund_no = #{refundNo} AND status = #{from}")
    int transitStatus(@Param("refundNo") String refundNo,
                      @Param("from") String from,
                      @Param("to") String to);

    /** 审核通过并落账时间。 */
    @Update("UPDATE refund_order SET status = 'REFUNDED', auditor_id = #{auditorId}, "
            + "audit_remark = #{auditRemark}, refunded_at = NOW(3) "
            + "WHERE refund_no = #{refundNo} AND status = 'PENDING'")
    int markRefunded(@Param("refundNo") String refundNo,
                     @Param("auditorId") Long auditorId,
                     @Param("auditRemark") String auditRemark);

    /**
     * 用户端「我的售后」分页：退款单左联订单，按订单归属当前客户过滤（归属隔离），
     * status 为空时返回全部。由 MyBatis-Plus {@link Page} 追加 LIMIT/OFFSET。
     */
    @Select("SELECT r.refund_no AS refundNo, o.order_no AS orderNo, r.status AS status, "
            + "r.amount_cent AS amountCent, r.created_at AS createdAt, "
            + "COALESCE(r.audit_remark, r.remark, r.reason_code) AS reason, "
            // 商品名优先取下单快照；历史演示单的 spu_title_snap 为空，回退到商品当前标题
            + "(SELECT COALESCE(NULLIF(oi.spu_title_snap, ''), p.title) FROM order_item oi "
            + " LEFT JOIN sku s ON s.id = oi.sku_id LEFT JOIN product p ON p.id = s.spu_id "
            + " WHERE oi.order_id = r.order_id ORDER BY oi.id LIMIT 1) AS itemName, "
            + "(SELECT COALESCE(SUM(oi.qty), 0) FROM order_item oi WHERE oi.order_id = r.order_id) AS itemQty, "
            + "(SELECT s.spu_id FROM order_item oi JOIN sku s ON s.id = oi.sku_id "
            + " WHERE oi.order_id = r.order_id ORDER BY oi.id LIMIT 1) AS productId "
            + "FROM refund_order r LEFT JOIN orders o ON o.id = r.order_id "
            + "WHERE o.customer_id = #{customerId} "
            + "AND (#{status} IS NULL OR #{status} = '' OR r.status = #{status}) "
            + "ORDER BY r.created_at DESC, r.id DESC")
    IPage<RefundListPO> selectMyPage(Page<RefundListPO> page,
                                     @Param("customerId") long customerId,
                                     @Param("status") String status);

    /** 用户端「我的售后」总数（与 selectMyPage 同一过滤条件）。 */
    @Select("SELECT COUNT(*) FROM refund_order r LEFT JOIN orders o ON o.id = r.order_id "
            + "WHERE o.customer_id = #{customerId} "
            + "AND (#{status} IS NULL OR #{status} = '' OR r.status = #{status})")
    long countMy(@Param("customerId") long customerId, @Param("status") String status);

    /** 在途售后单数（待审核 + 退款中）：个人中心角标只关心「还没结案」的那几笔。 */
    @Select("SELECT COUNT(*) FROM refund_order r LEFT JOIN orders o ON o.id = r.order_id "
            + "WHERE o.customer_id = #{customerId} AND r.status IN ('PENDING', 'REFUNDING')")
    long countMyInFlight(@Param("customerId") long customerId);

    /**
     * 用户端售后详情投影：联查订单号与归属客户，供控制器做归属隔离（非本人 → NOT_FOUND）。
     * reason 优先取自由文本 remark，回退到结构化 reason_code。
     */
    @Select("SELECT r.refund_no AS refundNo, o.order_no AS orderNo, r.status AS status, "
            + "r.amount_cent AS amountCent, r.type AS type, "
            + "COALESCE(r.remark, r.reason_code) AS reason, "
            + "r.applicant_type AS applicantType, r.appeal_count AS appealCount, "
            + "r.images_json AS imagesJson, "
            + "o.customer_id AS customerId, r.created_at AS createdAt, r.refunded_at AS refundedAt "
            + "FROM refund_order r LEFT JOIN orders o ON o.id = r.order_id "
            + "WHERE r.refund_no = #{refundNo}")
    RefundDetailPO selectDetailByRefundNo(@Param("refundNo") String refundNo);

    /**
     * 管理端退款审核详情（单笔联查）：退款单 + 订单头 + 客户 + 最近一条物流。
     * <p>审核员在一屏内判断该不该退，所以「谁买的、给了多少钱、发没发货、发货单号」
     * 必须跟退款单一起到位；全部用 LEFT JOIN，订单/客户被软删也不会让详情整页 404。</p>
     * <p>{@code reason} 与列表同口径（audit_remark → remark → reason_code 依次回退），
     * 保证列表与详情显示的是同一句话；{@code audit_remark} 单独出一列供详情页展示审核结论。</p>
     */
    @Select("SELECT r.refund_no AS refundNo, o.order_no AS orderNo, r.status AS status, "
            + "r.amount_cent AS amountCent, r.point_refund AS pointRefund, r.type AS type, "
            + "COALESCE(r.remark, r.reason_code) AS reason, r.applicant_type AS applicantType, "
            + "r.appeal_count AS appealCount, r.images_json AS imagesJson, "
            + "r.audit_remark AS auditRemark, r.order_item_id AS orderItemId, "
            + "r.created_at AS createdAt, r.refunded_at AS refundedAt, "
            + "o.id AS orderId, o.status AS orderStatus, o.pay_status AS payStatus, "
            + "o.after_sale_status AS afterSaleStatus, "
            + "o.goods_amount_cent AS goodsAmountCent, o.freight_cent AS freightCent, "
            + "o.point_deduct_cent AS pointDeductCent, o.discount_cent AS discountCent, "
            + "o.payable_cent AS payableCent, o.paid_cent AS paidCent, "
            + "o.created_at AS orderCreatedAt, o.paid_at AS paidAt, o.completed_at AS completedAt, "
            + "o.remark AS orderRemark, o.address_snapshot_json AS addressJson, "
            + "o.customer_id AS customerId, c.nickname AS customerName, c.phone AS customerPhone, "
            + "c.customer_no AS customerNo, c.type AS customerType, c.level_code AS customerLevel, "
            + "sh.carrier AS carrier, sh.tracking_no AS trackingNo, sh.shipped_at AS shippedAt "
            + "FROM refund_order r "
            + "LEFT JOIN orders o ON o.id = r.order_id "
            + "LEFT JOIN customer c ON c.id = o.customer_id "
            + "LEFT JOIN shipment sh ON sh.id = "
            + "  (SELECT MAX(s2.id) FROM shipment s2 WHERE s2.order_id = o.id) "
            + "WHERE r.refund_no = #{refundNo}")
    RefundAuditDetailPO selectAuditDetail(@Param("refundNo") String refundNo);

    /**
     * 审核详情的订单行：与订单详情同一取值口径（快照优先，缺失回退 sku/product 当前值）。
     * <p>{@code linePaidCent} 直接由 SQL 算出「行金额 − 分摊优惠」：退款上限与审核对账都用它，
     * 前端不需要再猜折扣怎么摊。</p>
     */
    @Select("SELECT oi.sku_id AS skuId, "
            + "COALESCE(oi.spu_title_snap, p.title) AS skuName, "
            + "COALESCE(oi.spec_snap, JSON_UNQUOTE(JSON_EXTRACT(sk.spec_json, '$.spec')), sk.sku_no) AS spec, "
            + "oi.qty AS qty, oi.unit_price_cent AS unitPriceCent, oi.amount_cent AS amountCent, "
            + "oi.discount_alloc_cent AS discountAllocCent, "
            + "oi.amount_cent - oi.discount_alloc_cent AS linePaidCent, "
            + "oi.shipped_qty AS shippedQty, "
            + "COALESCE(co.status, CASE WHEN oi.custom_flag = 1 THEN 'WAIT_DRAFT' ELSE 'NONE' END) AS customStatus "
            + "FROM order_item oi "
            + "LEFT JOIN sku sk ON sk.id = oi.sku_id "
            + "LEFT JOIN product p ON p.id = sk.spu_id "
            + "LEFT JOIN custom_order co ON co.order_item_id = oi.id "
            + "WHERE oi.order_id = #{orderId} ORDER BY oi.id")
    List<RefundOrderLinePO> selectOrderLines(@Param("orderId") long orderId);

    /**
     * 审核 / 回调写回状态（DB 化退款仓储的 upsert 更新分支）：仅推进会随业务变化的字段，
     * refunded_at 仅当传入非空时落账（保持为空时不覆盖历史值）。返回 0 行属异常（单号不存在）。
     */
    @Update("UPDATE refund_order SET status = #{status}, appeal_count = #{appealCount}, "
            + "refunded_at = CASE WHEN #{refundedAt} IS NULL THEN refunded_at ELSE #{refundedAt} END, "
            + "audit_remark = CASE WHEN #{auditRemark} IS NULL THEN audit_remark ELSE #{auditRemark} END "
            + "WHERE refund_no = #{refundNo}")
    int updateRefundState(@Param("refundNo") String refundNo,
                          @Param("status") String status,
                          @Param("appealCount") int appealCount,
                          @Param("refundedAt") LocalDateTime refundedAt,
                          @Param("auditRemark") String auditRemark);
}
