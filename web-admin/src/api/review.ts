/**
 * 评价管理 API（管理端，V56）。
 *
 * 口径：
 * - 列表不按状态强制过滤（运营要能看到已隐藏的并恢复）；
 * - 状态变更只改 status，正文/星级/图片原样保留；
 * - 计数与 ID 都可能被后端序列化成字符串，页面侧统一 `Number()`。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

export interface AdminReviewVO {
  reviewId: string;
  productId: number | string;
  productTitle: string;
  rating: number;
  content: string;
  /** 评价图 fileId 数组 */
  images: string[];
  /** 已脱敏的评价人（匿名为「匿名用户」） */
  userLabel: string;
  anonymous: boolean;
  status: 'NORMAL' | 'HIDDEN' | string;
  createdAt: number | string;
}

export interface AdminReviewQuery extends PageQuery {
  productId?: number | string;
  rating?: number;
  status?: string;
}

export interface ReviewStatusCmd {
  status: 'NORMAL' | 'HIDDEN';
  reason?: string;
}

export const reviewAdminApi = {
  list: (query: AdminReviewQuery) => http.get<PageResult<AdminReviewVO>>('/admin/reviews', query),
  updateStatus: (reviewId: number | string, cmd: ReviewStatusCmd) =>
    http.put<AdminReviewVO>(`/admin/reviews/${reviewId}/status`, cmd),
};
