/**
 * 商品浏览足迹 API（用户端，对齐 FootprintController，2026-11-16）。
 *
 * 详情页加载时静默上报 `record`，服务端按商品去重（重复浏览只刷新时间 + 次数）；
 * 未登录时后端也是静默成功，前端不必判断登录态。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

export interface FootprintVO {
  productId: string;
  productName: string;
  mainImage?: string | null;
  priceCent: number | string;
  priceYuan?: string | null;
  onShelf: boolean;
  viewCount: number | string;
  favorited: boolean;
  lastViewedAt?: number | string | null;
}

export const footprintApi = {
  list: (query: PageQuery) => http.get<PageResult<FootprintVO>>('/footprints', query),
  record: (productId: number | string) => http.post<void>(`/footprints/${productId}`),
  clear: () => http.delete<number>('/footprints'),
};
