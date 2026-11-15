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
};
