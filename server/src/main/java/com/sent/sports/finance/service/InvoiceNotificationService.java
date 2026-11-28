package com.sent.sports.finance.service;

/**
 * 开票链路站内信：成功、异常、驳回、用户端申请被拒四类通知统一在此收口。
 * <p>只依赖 notify 模块的 service 接口与中立 DTO（ArchUnit 跨模块约束）。</p>
 */
public interface InvoiceNotificationService {

    /** 发票开具成功（管理端新建开票 / 已有申请换开都走这里）。 */
    void issued(long invoiceId);

    /** 开票失败 / 票面异常被标记：原因必须带进正文。 */
    void exception(long invoiceId, String reason);

    /** 开票申请被财务驳回：原因必须带进正文。 */
    void rejected(long invoiceId, String reason);

    /** 用户端提交申请时因订单状态不符被拒：此时还没有发票行。 */
    void applicationRejected(long customerId, String orderNo, String reason);
}
