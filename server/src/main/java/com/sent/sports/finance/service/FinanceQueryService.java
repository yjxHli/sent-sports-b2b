package com.sent.sports.finance.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.finance.dto.admin.AdminFinanceDtos;

/**
 * 财务查询门面（P4-47 接口契约，跨模块可用）。
 * <p>应收台账 / 发票 / 合同列表与开票动作均已接入真实 Mapper 分页查询
 * （{@code FinanceQueryServiceImpl}）。契约（接口签名）保持稳定。</p>
 */
public interface FinanceQueryService {

    /**
     * 应收台账分页：含逾期天数。
     * <p>原 P4 桩恒返回空列表，会让「确认收货 → 生成应收」之后的台账页/回款页看不到任何数据；
     * 现按 {@code receivable} 真库分页，订单号联查 {@code orders}。</p>
     *
     * @param openOnly 只要未结清（{@code status <> 'CLOSED'}）。核销工作台必须为 {@code true}：
     *                 已结清应收不可再核销，且过滤条件必须下推 SQL，否则前端先取一页再剔行
     *                 会出现「共 N 条但某页只剩几条」的错位。
     */
    PageResult<AdminFinanceDtos.ReceivableVO> receivables(PageQuery pageQuery, Long customerId,
                                                          String status, boolean openOnly);

    /** 发票申请分页：票面金额联查订单应付金额。 */
    PageResult<AdminFinanceDtos.InvoiceVO> invoices(PageQuery pageQuery, String status);

    /**
     * 合同分页：金额与状态取自 V10 补列，到期日换算成毫秒。
     *
     * @param status   合同单据状态（ACTIVE / EXPIRED …），空白表示不限
     * @param dueState 派生到期状态 EXPIRED / SOON（30 天内）/ NORMAL，空白表示不限。
     *                 由 {@code expire_date} 在 SQL 里判定，避免前端先取一页再过滤导致漏数据。
     */
    PageResult<AdminFinanceDtos.ContractVO> contracts(PageQuery pageQuery, String status, String dueState);

    /**
     * 回款单分页（收口阶段）：写入侧登记/核销/冲销早已真落库，但查询侧缺失导致页面只能显示本次登记结果。
     * 现按 {@code payment_record} 真库分页，可按客户 / 状态过滤。
     */
    PageResult<AdminFinanceDtos.PaymentRecordVO> paymentRecords(PageQuery pageQuery,
                                                                Long customerId, String status);

    /**
     * 按订单号新建开票申请并开具（财务代客开票）。
     * <p>发票管理页此前无任何申请来源、列表恒空，工具栏开票弹窗只能提交写死的 invoiceId=1 → 404。
     * 用 {@code INSERT ... SELECT FROM orders} 一次拿到真实 order_id/customer_id，避免跨模块 Java 依赖。</p>
     */
    AdminFinanceDtos.InvoiceIssueVO applyInvoice(AdminFinanceDtos.InvoiceApplyCmd cmd);

    /** 发票开具（换开）：落发票号 / PDF 地址 / 开具时间，返回开具后快照。 */
    Object issueInvoice(long invoiceId, String pdfUrl, String remark);
}
