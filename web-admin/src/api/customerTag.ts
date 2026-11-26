/**
 * 客户标签管理 API（管理端，V63，对应 AdminCustomerTagController）。
 *
 * 口径：
 * - 读目录带 `includeDisabled=true`：维护页必须能看到停用项，否则停用之后再也点不回来；
 * - 分类的中文名与配色键由后端下发，页面**不写**「EXCELLENT → 绿色」的映射表；
 * - 写接口（新增 / 改名 / 停用 / 删除）需要 `customer:tag:manage`（精确匹配），
 *   持 `'*'` 的业务角色看不到这个菜单。
 */
import { http } from '@/utils/http';

export interface CustomerTagVO {
  tagId: number | string;
  name: string;
  category: string;
  /** 中文分类名，如「风险留意」 */
  categoryLabel: string;
  /** 配色键：excellent / neutral / risk / malicious */
  colorKey: string;
  status: string;
  sortNo?: number | string | null;
}

export interface CustomerTagForm {
  name: string;
  category: string;
}

export const customerTagAdminApi = {
  catalog: (includeDisabled = true) =>
    http.get<CustomerTagVO[]>('/admin/customer-tags', { includeDisabled }),
  create: (form: CustomerTagForm) => http.post<CustomerTagVO>('/admin/customer-tags', form),
  update: (tagId: number | string, form: CustomerTagForm) =>
    http.put<CustomerTagVO>(`/admin/customer-tags/${tagId}`, form),
  updateStatus: (tagId: number | string, status: string) =>
    http.put<CustomerTagVO>(`/admin/customer-tags/${tagId}/status`, { status }),
  remove: (tagId: number | string) =>
    http.delete<void>(`/admin/customer-tags/${tagId}`),
};

/** 四大分类选项：与后端 TagCategory 一一对应（顺序即弹窗分组顺序） */
export const TAG_CATEGORY_OPTIONS = [
  { value: 'EXCELLENT', label: '优质 / 正面', colorKey: 'excellent' },
  { value: 'NEUTRAL', label: '中性普通', colorKey: 'neutral' },
  { value: 'RISK', label: '风险留意', colorKey: 'risk' },
  { value: 'MALICIOUS', label: '恶意', colorKey: 'malicious' },
] as const;
