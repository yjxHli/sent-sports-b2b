package com.sent.sports.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.finance.mapper.po.InvoiceListItemPO;
import com.sent.sports.finance.mapper.po.InvoicePdfHeaderPO;
import com.sent.sports.finance.mapper.po.InvoicePdfItemPO;
import com.sent.sports.finance.mapper.po.InvoiceRequestPO;
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

    @Select("SELECT ir.id AS invoiceId, ir.invoice_no AS invoiceNo, ir.customer_id AS customerId, "
            + "COALESCE(o.payable_cent, 0) AS amountCent, ir.status AS status, "
            + "ir.invoice_url AS pdfUrl, ir.created_at AS createdAt "
            + "FROM invoice_request ir "
            + "LEFT JOIN orders o ON o.id = ir.order_id "
            + "WHERE (#{status} IS NULL OR #{status} = '' OR ir.status = #{status}) "
            + "ORDER BY ir.created_at DESC, ir.id DESC")
    IPage<InvoiceListItemPO> selectInvoicePage(Page<InvoiceListItemPO> page,
                                               @Param("status") String status);

    @Select("SELECT COUNT(*) FROM invoice_request ir "
            + "WHERE (#{status} IS NULL OR #{status} = '' OR ir.status = #{status})")
    long countInvoice(@Param("status") String status);

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
