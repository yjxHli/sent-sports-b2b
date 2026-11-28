/**
 * 售后 API（用户端，对齐 RefundController P4-22）。
 * 申请退款需逐行金额（分）与凭证；我的售后为只读列表/详情/申诉。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

export interface RefundVO {
  refundNo: string;
  orderNo: string;
  status: string | null;
  amountCent?: number | null;
  amountYuan?: string | null;
  /** 申请时间（毫秒） */
  createdAtMillis?: number | string | null;
  reason?: string | null;
  /** 该订单第一件商品名 + 总件数：售后列表按淘宝版式展示商品信息 */
  itemName?: string | null;
  itemQty?: number | string | null;
  /** 第一件商品的 SPU，供「查看商品」跳转 */
  productId?: string | null;
}

/** 售后角标计数：total 全部售后单，inFlight 待审核 + 退款中 */
export interface RefundCountVO {
  total: number | string;
  inFlight: number | string;
}

export interface RefundDetailVO {
  refundNo: string;
  orderNo: string;
  status: string | null;
  amountCent?: number | null;
  amountYuan?: string | null;
  type?: string | null;
  reason?: string | null;
  applicantType?: string | null;
  appealCount?: number | null;
  /** 申请时上传的凭证图片 fileId，配合 fileApi.url() 渲染 */
  images?: string[] | null;
  createdAtMillis?: number | null;
  refundedAtMillis?: number | null;
}

export const refundApi = {
  apply: (cmd: {
    orderNo: string;
    lines: { skuId: number; amountCent: number }[];
    reason: string;
    proof: string;
    /** 凭证照片 fileId（先调 fileApi.upload 拿到） */
    images?: string[];
  }) => http.post<RefundVO>('/refunds', cmd),
  list: (query: PageQuery & { status?: string }) =>
    http.get<PageResult<RefundVO>>('/refunds', query),
  /** 售后角标计数（个人中心「退款/售后」不再恒为 0） */
  count: () => http.get<RefundCountVO>('/refunds/count'),
  detail: (refundNo: string) =>
    http.get<RefundDetailVO>(`/refunds/${refundNo}`),
};
