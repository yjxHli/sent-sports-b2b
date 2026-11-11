package com.sent.sports.refund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.refund.mapper.po.RefundAuditItemPO;
import com.sent.sports.refund.mapper.po.RefundDetailPO;
import com.sent.sports.refund.mapper.po.RefundListPO;
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
     * 由 MyBatis-Plus {@link Page} 追加 LIMIT/OFFSET，总数另由 {@link #countAudit} 统计。
     */
    @Select("SELECT r.refund_no AS refundNo, o.order_no AS orderNo, r.status AS status, "
            + "r.amount_cent AS amountCent, "
            + "COALESCE(r.audit_remark, r.remark, r.reason_code) AS reason, r.created_at AS createdAt "
            + "FROM refund_order r LEFT JOIN orders o ON o.id = r.order_id "
            + "WHERE (#{status} IS NULL OR #{status} = '' OR r.status = #{status}) "
            + "ORDER BY r.created_at DESC, r.id DESC")
    IPage<RefundAuditItemPO> selectAuditPage(Page<RefundAuditItemPO> page,
                                             @Param("status") String status);

    /** 管理端退款审核列表总数（与 selectAuditPage 同一过滤条件）。 */
    @Select("SELECT COUNT(*) FROM refund_order r "
            + "WHERE (#{status} IS NULL OR #{status} = '' OR r.status = #{status})")
    long countAudit(@Param("status") String status);

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
            + "r.amount_cent AS amountCent, r.created_at AS createdAt "
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

    /**
     * 用户端售后详情投影：联查订单号与归属客户，供控制器做归属隔离（非本人 → NOT_FOUND）。
     * reason 优先取自由文本 remark，回退到结构化 reason_code。
     */
    @Select("SELECT r.refund_no AS refundNo, o.order_no AS orderNo, r.status AS status, "
            + "r.amount_cent AS amountCent, r.type AS type, "
            + "COALESCE(r.remark, r.reason_code) AS reason, "
            + "r.applicant_type AS applicantType, r.appeal_count AS appealCount, "
            + "o.customer_id AS customerId, r.created_at AS createdAt, r.refunded_at AS refundedAt "
            + "FROM refund_order r LEFT JOIN orders o ON o.id = r.order_id "
            + "WHERE r.refund_no = #{refundNo}")
    RefundDetailPO selectDetailByRefundNo(@Param("refundNo") String refundNo);

    /**
     * 审核 / 回调写回状态（DB 化退款仓储的 upsert 更新分支）：仅推进会随业务变化的字段，
     * refunded_at 仅当传入非空时落账（保持为空时不覆盖历史值）。返回 0 行属异常（单号不存在）。
     */
    @Update("UPDATE refund_order SET status = #{status}, appeal_count = #{appealCount}, "
            + "refunded_at = CASE WHEN #{refundedAt} IS NULL THEN refunded_at ELSE #{refundedAt} END "
            + "WHERE refund_no = #{refundNo}")
    int updateRefundState(@Param("refundNo") String refundNo,
                          @Param("status") String status,
                          @Param("appealCount") int appealCount,
                          @Param("refundedAt") LocalDateTime refundedAt);
}
