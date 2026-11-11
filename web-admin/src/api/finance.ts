/**
 * 财务 API（P6-19，对应 AdminFinanceController / RefundController）。
 * 核销 / 冲销 / 授信为危险操作，必须带 X-Confirm-Token（见 utils/confirm.ts）。
 */
import { http, type SentRequestConfig } from '@/utils/http';
import type {
  PageQuery, PageResult, ReceivableVO, WriteOffVO, RefundVO,
} from '@sent/shared';

export interface RecordCmd {
  customerId: number;
  amountCent: number;
  serialNo: string;
  remark?: string;
}

export interface RecordVO {
  paymentNo: string;
  customerId: number;
  amountCent: number;
  serialNo: string;
  status: string;
  createdAtMillis: number;
  remark?: string;
}

/** 回款列表行：后端 GET /admin/payment-records 真分页返回 */
export interface PaymentRecordVO {
  paymentNo: string;
  customerId: number;
  amountCent: number;
  writtenOffCent: number;
  serialNo: string;
  status: string;
  receivedDateMillis: number;
  createdAtMillis: number;
  remark?: string | null;
  reversedReason?: string | null;
}

export interface PaymentRecordQuery extends PageQuery {
  customerId?: number;
  status?: string;
}

export interface WriteOffLineCmd {
  receivableId: number;
  amount: number;
}

export interface WriteOffCmd {
  customerId: number;
  lines: WriteOffLineCmd[];
}

export interface ReverseVO {
  paymentNo: string;
  status: string;
}

export interface CreditCmd {
  totalCent: number;
  accountPeriodDays: number;
  validFromMillis: number;
  validToMillis: number;
  remark?: string;
}

export interface CreditVO {
  customerId: number;
  totalCent: number;
  availableCent: number;
  validToMillis: number;
  status: string;
}

/** 发票 / 合同列表契约：后端收口阶段已提供真实分页查询（发票金额联查订单应付金额） */
export interface InvoiceVO {
  invoiceId: number;
  invoiceNo: string;
  customerId: number;
  amountCent: number;
  status: string;
  pdfUrl?: string;
  createdAt?: number;
}

/**
 * 新建开票（财务代客开票）：按订单号一次完成「创建开票申请 + 开具」。
 * 系统原本没有申请创建入口，invoice_request 恒为空，旧弹窗写死 invoiceId=1 提交必 404。
 */
export interface InvoiceApplyCmd {
  orderNo: string;
  title: string;
  taxNo?: string;
  email?: string;
  /** 留空则由后端按 /api/admin/invoices/{id}/pdf 生成（票面后端现场渲染） */
  pdfUrl?: string;
  remark?: string;
}

export interface InvoiceIssueVO {
  invoiceId: number;
  invoiceNo: string;
  orderNo: string;
  status: string;
  pdfUrl: string;
}

export interface ContractVO {
  contractId: number;
  contractNo: string;
  customerId: number;
  amountCent: number;
  status: string;
  validToMillis?: number;
}

export interface RefundAuditCmd {
  approved: boolean;
  remark?: string;
}

/** 需要确认令牌的写操作统一走的请求头 */
function confirmHeaders(confirmToken: string): { headers: Record<string, string> } {
  return { headers: { 'X-Confirm-Token': confirmToken } };
}

export const financeApi = {
  /**
   * 应收台账：`openOnly=true` 由 SQL 过滤 `status <> 'CLOSED'`（核销工作台只要未结清），
   * 筛选与分页都用同一条件，避免前端先取一页再剔行导致总数/行数错位。
   */
  receivables: (query: PageQuery & { customerId?: number; status?: string; openOnly?: boolean }) =>
    http.get<PageResult<ReceivableVO>>('/admin/receivables', query),
  /** 回款列表：真查 payment_record，不再只展示本次会话登记结果 */
  paymentRecords: (query: PaymentRecordQuery) =>
    http.get<PageResult<PaymentRecordVO>>('/admin/payment-records', query),
  record: (cmd: RecordCmd, config?: SentRequestConfig) =>
    http.post<RecordVO>('/admin/payment-records', cmd, config),
  writeOff: (paymentNo: string, cmd: WriteOffCmd, confirmToken: string) =>
    http.post<WriteOffVO>(
      `/admin/payment-records/${paymentNo}/write-off`, cmd, confirmHeaders(confirmToken),
    ),
  reverse: (paymentNo: string, reason: string, confirmToken: string) =>
    http.post<ReverseVO>(
      `/admin/payment-records/${paymentNo}/reverse`, { reason }, confirmHeaders(confirmToken),
    ),
  credit: (customerId: number, cmd: CreditCmd, confirmToken: string) =>
    http.post<CreditVO>(
      `/admin/enterprise/${customerId}/credit`, cmd, confirmHeaders(confirmToken),
    ),

  /** 按订单号新建开票申请并开具（发票列表为空时也能开票） */
  applyInvoice: (cmd: InvoiceApplyCmd) =>
    http.post<InvoiceIssueVO>('/admin/invoices', cmd),
  /** 对已有开票申请开票 / 换开（补 PDF、换发票文件） */
  issueInvoice: (invoiceId: number, pdfUrl?: string, remark?: string) =>
    http.post<void>(`/admin/invoices/${invoiceId}/issue`, { pdfUrl, remark }),

  /**
   * 发票 PDF（后端现场生成的增值税电子普通发票，非静态文件）。
   * 预览取 Blob 塞 iframe；下载按发票号命名另存，不用服务端 attachment 的默认文件名。
   */
  invoicePdfBlob: (invoiceId: number) =>
    http.getBlob(`/admin/invoices/${invoiceId}/pdf`),
  downloadInvoicePdf: (invoiceId: number, invoiceNo?: string) =>
    http.download(
      `/admin/invoices/${invoiceId}/pdf`,
      `发票-${invoiceNo && invoiceNo.trim() ? invoiceNo.trim() : invoiceId}.pdf`,
    ),

  /** 审核列表来自管理端 /admin/refunds（真实分页，支持 status 过滤）；审核端点已就绪 */
  refunds: (query: PageQuery & { status?: string }) =>
    http.get<PageResult<RefundVO>>('/admin/refunds', query),
  refundDetail: (refundNo: string) => http.get<RefundVO>(`/refunds/${refundNo}`),
  /** 审核通过需 X-Confirm-Token；驳回不需要 */
  auditRefund: (refundNo: string, cmd: RefundAuditCmd, confirmToken?: string) =>
    http.post<RefundVO>(
      `/admin/refunds/${refundNo}/audit`, cmd,
      confirmToken ? confirmHeaders(confirmToken) : {},
    ),

  /** 发票 / 合同列表：后端真实分页查询 */
  invoices: (query: PageQuery & { status?: string }) =>
    http.get<PageResult<InvoiceVO>>('/admin/invoices', query),
  /**
   * 合同列表：`status` 是单据状态（ACTIVE/EXPIRED），`dueState` 是由到期日派生的
   * 「已到期 / 30 天内到期 / 正常」，两者都在 SQL 里过滤，前端不再二次筛。
   */
  contracts: (query: PageQuery & { status?: string; dueState?: string }) =>
    http.get<PageResult<ContractVO>>('/admin/contracts', query),
};
