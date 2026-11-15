package com.sent.sports.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.finance.mapper.po.InvoiceApplyRow;
import com.sent.sports.finance.mapper.po.InvoiceListItemPO;
import com.sent.sports.finance.mapper.po.InvoicePdfHeaderPO;
import com.sent.sports.finance.mapper.po.InvoicePdfItemPO;
import com.sent.sports.finance.mapper.po.InvoiceRequestPO;
import com.sent.sports.finance.mapper.po.MyOrderStatePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 开票申请 Mapper（收口阶段）。
 * <p>列表分页由 MyBatis-Plus {@link Page} + PaginationInnerInterceptor 追加 LIMIT/OFFSET，
 * 总数单独统计；票面金额不落 invoice_request，直接联查订单应付金额，避免双写不一致。</p>
 */
@Mapper
public interface InvoiceMapper extends BaseMapper<InvoiceRequestPO> {

    /**
     * 发票关键字条件：发票 ID / 发票号 / 客户 ID 三选一模糊匹配。
     * <p>列表与 {@link #countInvoice} 必须共用同一段 SQL，否则「共 N 条」会与行数错位；
     * 条件写进 WHERE 而不是前端过滤，是因为 {@code PageQuery.MAX_SIZE = 100} 会把更大的结果集截断。</p>
     */
    String INVOICE_KEYWORD_WHERE = " AND (#{keyword} IS NULL OR #{keyword} = '' "
            + "OR ir.invoice_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR CAST(ir.id AS CHAR) LIKE CONCAT('%', #{keyword}, '%') "
            + "OR CAST(ir.customer_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%')) ";

    @Select("SELECT ir.id AS invoiceId, ir.invoice_no AS invoiceNo, ir.customer_id AS customerId, "
            + "COALESCE(o.payable_cent, 0) AS amountCent, ir.status AS status, "
            + "ir.invoice_url AS pdfUrl, ir.created_at AS createdAt "
            + "FROM invoice_request ir "
            + "LEFT JOIN orders o ON o.id = ir.order_id "
            + "WHERE (#{status} IS NULL OR #{status} = '' OR ir.status = #{status}) "
            + INVOICE_KEYWORD_WHERE
            + " ORDER BY ir.created_at DESC, ir.id DESC")
    IPage<InvoiceListItemPO> selectInvoicePage(Page<InvoiceListItemPO> page,
                                               @Param("status") String status,
                                               @Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM invoice_request ir "
            + "WHERE (#{status} IS NULL OR #{status} = '' OR ir.status = #{status}) "
            + INVOICE_KEYWORD_WHERE)
    long countInvoice(@Param("status") String status, @Param("keyword") String keyword);

    /**
     * 按订单号创建开票申请：{@code INSERT ... SELECT} 直接取 orders 的主键与客户，
     * 订单不存在时返回 0 行（业务侧据此抛 404）。SQL 层联查不引入对 order 模块的 Java 依赖。
     */
    @Insert("INSERT INTO invoice_request "
            + "(order_id, customer_id, title, tax_no, email, status, created_at, updated_at) "
            + "SELECT o.id, o.customer_id, #{title}, #{taxNo}, #{email}, 'APPLIED', NOW(3), NOW(3) "
            + "FROM orders o WHERE o.order_no = #{orderNo}")
    int insertFromOrder(@Param("orderNo") String orderNo,
                        @Param("title") String title,
                        @Param("taxNo") String taxNo,
                        @Param("email") String email);

    /** 取该订单最新一条开票申请主键（刚插入的那条）。 */
    @Select("SELECT ir.id FROM invoice_request ir "
            + "JOIN orders o ON o.id = ir.order_id WHERE o.order_no = #{orderNo} "
            + "ORDER BY ir.id DESC LIMIT 1")
    Long selectLatestIdByOrderNo(@Param("orderNo") String orderNo);

    /** 开票 / 换开：落发票号、PDF 地址与开具时间；返回 0 行表示发票申请不存在。 */
    @Update("UPDATE invoice_request SET status = 'ISSUED', invoice_no = #{invoiceNo}, "
            + "invoice_url = #{pdfUrl}, issued_at = NOW(3) WHERE id = #{invoiceId}")
    int markIssued(@Param("invoiceId") long invoiceId,
                   @Param("invoiceNo") String invoiceNo,
                   @Param("pdfUrl") String pdfUrl);

    // ------------------------------------------------------------------
    // 用户端：我的订单申请开票（订单完成后可开）
    // ------------------------------------------------------------------

    /**
     * 当前客户在该订单上最新一条开票申请。
     * <p>带 {@code customer_id} 条件，越权查他人订单的发票拿不到数据（返回 null）。</p>
     */
    @Select("SELECT ir.id AS invoiceId, o.order_no AS orderNo, ir.status, ir.title, ir.tax_no AS taxNo, "
            + "ir.type, ir.email, ir.invoice_no AS invoiceNo, ir.invoice_url AS pdfUrl, "
            + "COALESCE(o.payable_cent, 0) AS amountCent, ir.created_at AS createdAt "
            + "FROM invoice_request ir JOIN orders o ON o.id = ir.order_id "
            + "WHERE o.order_no = #{orderNo} AND ir.customer_id = #{customerId} "
            + "ORDER BY ir.id DESC LIMIT 1")
    InvoiceApplyRow selectMyLatestByOrderNo(@Param("orderNo") String orderNo,
                                            @Param("customerId") long customerId);

    /**
     * 订单主状态 + 支付状态（同时校验归属）：查不到说明订单不存在或不属于当前账户。
     *
     * <p>可开票口径（2026-11-15 用户要求收紧）：订单必须 <b>已完成</b>（{@code COMPLETED}），
     * 且支付状态仍在 {@code PAID / PART_REFUNDED}。在途订单（待发货 / 待收货）不开票——
     * 货未到手可能退换，票面金额与实际成交不一致；整单全退的钱已退回，也不该再开票。</p>
     */
    @Select("SELECT o.status AS status, o.pay_status AS payStatus FROM orders o "
            + "WHERE o.order_no = #{orderNo} AND o.customer_id = #{customerId}")
    MyOrderStatePO selectOrderStateOfMyOrder(@Param("orderNo") String orderNo,
                                             @Param("customerId") long customerId);

    /**
     * 用户端提交开票申请：{@code INSERT ... SELECT} 直接取订单主键与客户，归属在 SQL 里校验。
     * <p>{@code NOT EXISTS} 兜住「同一订单已有待开/已开发票」的重复提交，
     * 返回 0 行时业务侧回读已有申请（幂等），而不是报错。</p>
     */
    @Insert("INSERT INTO invoice_request "
            + "(order_id, customer_id, title, tax_no, type, email, status, created_at, updated_at) "
            + "SELECT o.id, o.customer_id, #{title}, #{taxNo}, #{type}, #{email}, 'APPLIED', NOW(3), NOW(3) "
            + "FROM orders o WHERE o.order_no = #{orderNo} AND o.customer_id = #{customerId} "
            + "AND NOT EXISTS (SELECT 1 FROM invoice_request ir WHERE ir.order_id = o.id "
            + "AND ir.status IN ('APPLIED', 'ISSUED'))")
    int insertUserApplication(@Param("orderNo") String orderNo,
                              @Param("customerId") long customerId,
                              @Param("title") String title,
                              @Param("taxNo") String taxNo,
                              @Param("type") String type,
                              @Param("email") String email);

    // ------------------------------------------------------------------
    // 发票 PDF 票面数据（一次性取票头，再一次取明细）
    // ------------------------------------------------------------------

    /**
     * 票头：金额取订单应付金额，抬头/税号取开票申请，客户昵称作为抬头兜底。
     * 一次联查完成，不引入对 order / customer 模块的 Java 依赖。
     */
    @Select("SELECT ir.id AS invoiceId, ir.invoice_no AS invoiceNo, ir.status AS status, "
            + "ir.issued_at AS issuedAt, ir.created_at AS createdAt, ir.title AS title, "
            + "ir.tax_no AS taxNo, ir.customer_id AS customerId, ir.order_id AS orderId, "
            + "o.order_no AS orderNo, COALESCE(o.payable_cent, 0) AS payableCent, "
            + "c.nickname AS customerNickname, o.remark AS remark "
            + "FROM invoice_request ir "
            + "LEFT JOIN orders o ON o.id = ir.order_id "
            + "LEFT JOIN customer c ON c.id = ir.customer_id "
            + "WHERE ir.id = #{invoiceId}")
    InvoicePdfHeaderPO selectPdfHeader(@Param("invoiceId") long invoiceId);

    /**
     * 明细：{@code spu_title_snap / spec_snap} 在历史订单行上可能为 NULL，
     * 这里联查 product 给标题兜底（规格缺失由组装层印「无」）。
     */
    @Select("SELECT oi.sku_id AS skuId, oi.spu_title_snap AS spuTitleSnap, "
            + "oi.spec_snap AS specSnap, p.title AS productTitle, "
            + "oi.qty AS qty, oi.unit_price_cent AS unitPriceCent, oi.amount_cent AS amountCent "
            + "FROM order_item oi "
            + "LEFT JOIN sku s ON s.id = oi.sku_id "
            + "LEFT JOIN product p ON p.id = s.spu_id "
            + "WHERE oi.order_id = #{orderId} ORDER BY oi.id")
    List<InvoicePdfItemPO> selectPdfItems(@Param("orderId") long orderId);
}
