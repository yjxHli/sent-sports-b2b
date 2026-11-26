/**
 * 客户标签 API（V64，对应 AdminCustomerTagController）。
 *
 * 标签只有客服与后台可见，所以这里没有任何「用户自查」入口——
 * 客服端只做两件事：读目录、读写「这个客户身上有什么标签」。
 */
import { http } from '@/utils/http';

/** 四大分类；后端下发中文名与配色键，前端不写死映射表 */
export type TagCategory = 'EXCELLENT' | 'NEUTRAL' | 'RISK' | 'MALICIOUS';

export interface CustomerTagVO {
  tagId: number;
  name: string;
  category: string;
  /** 中文分类名，如「优质 / 正面」 */
  categoryLabel: string;
  /** 配色键：excellent / neutral / risk / malicious */
  colorKey: string;
  status: string;
  sortNo?: number | null;
}

export const customerTagApi = {
  /** 标签目录：客服弹窗只要可选项（不含已停用） */
  catalog: () => http.get<CustomerTagVO[]>('/admin/customer-tags', { includeDisabled: false }),
  /** 某客户身上的标签 */
  ofCustomer: (customerId: number) =>
    http.get<CustomerTagVO[]>(`/admin/customers/${customerId}/tags`),
  /** 全量提交勾选结果（空数组 = 全部取消） */
  apply: (customerId: number, tagIds: number[]) =>
    http.put<CustomerTagVO[]>(`/admin/customers/${customerId}/tags`, { tagIds }),
};
