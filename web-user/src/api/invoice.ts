/**
 * 开票 API（用户端）。
 *
 * 订单支付完成后客户可以自己申请开票：申请入口在订单列表与订单详情，
 * 状态查询用于把按钮换成「已提交 / 已开具（下载 PDF）」。
 *
 * 约定：
 * - `amountCent` 由后端按 `orders.payable_cent` 联查返回，前端不要自行计算票面金额；
 * - 同一订单重复申请是幂等的（后端 SQL 用 NOT EXISTS 兜住），不会开出两张票；
 * - 从未申请时 `latest` 返回 `null`，不要当成错误处理。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

/** 发票类型：普通发票 / 增值税专用发票（专用票必须带税号） */
export type InvoiceType = 'NORMAL' | 'SPECIAL';

export interface InvoiceVO {
  invoiceId?: string | null;
  orderNo?: string | null;
  /** APPLIED（已提交待开具）/ ISSUED（已开具，可下载 PDF） */
  status?: string | null;
  title?: string | null;
  taxNo?: string | null;
  type?: string | null;
  email?: string | null;
  amountCent?: number | null;
  amountYuan?: string | null;
  invoiceNo?: string | null;
  pdfUrl?: string | null;
  createdAtMillis?: number | null;
}

/** 「我的发票」列表行（已开具 / 申请中）。 */
export interface InvoiceRowVO {
  invoiceId?: string | null;
  orderNo?: string | null;
  invoiceNo?: string | null;
  /** APPLIED / ISSUED / REJECTED / EXCEPTION */
  status?: string | null;
  title?: string | null;
  type?: string | null;
  amountCent?: number | null;
  amountYuan?: string | null;
  createdAtMillis?: number | null;
  issuedAtMillis?: number | null;
}

/** 「我的发票 — 未申请」行：可开票但还没有有效申请的订单。 */
export interface PendingInvoiceVO {
  orderNo?: string | null;
  amountCent?: number | null;
  amountYuan?: string | null;
  firstTitle?: string | null;
  completedAtMillis?: number | null;
}

export const invoiceApi = {
  /** 提交开票申请（已支付订单；重复提交返回已有申请） */
  apply: (cmd: {
    orderNo: string;
    title: string;
    taxNo?: string;
    type?: InvoiceType;
    email?: string;
  }) => http.post<InvoiceVO>('/invoices', cmd),

  /** 查询该订单最新开票申请；从未申请返回 null */
  latest: (orderNo: string) =>
    http.get<InvoiceVO | null>(`/invoices/order/${orderNo}`),

  /**
   * 我的发票分页。
   * @param status ISSUED=已开具 / OPEN=申请中（待开 + 驳回 + 异常）/ 不传=全部
   */
  mine: (query: PageQuery & { status?: string; keyword?: string }) =>
    http.get<PageResult<InvoiceRowVO>>('/invoices', query),

  /** 未申请开票的订单（已完成且仍可开票，排除已有有效申请的） */
  pending: (query: PageQuery) =>
    http.get<PageResult<PendingInvoiceVO>>('/invoices/pending', query),

  /** 发票 PDF（带令牌以 blob 取回，再交给浏览器预览 / 下载） */
  pdfBlob: (invoiceId: number | string) =>
    http.getBlob(`/invoices/${invoiceId}/pdf`),

  downloadPdf: (invoiceId: number | string, invoiceNo?: string | null) =>
    http.download(
      `/invoices/${invoiceId}/pdf?download=true`,
      `发票-${invoiceNo && invoiceNo.trim() ? invoiceNo.trim() : invoiceId}.pdf`,
    ),
};
