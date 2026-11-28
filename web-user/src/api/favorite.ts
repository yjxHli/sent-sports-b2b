/**
 * 商品收藏 API（用户端，对齐 FavoriteController，2026-11-16）。
 *
 * 返回的 `productId` 是字符串形式的 SPU 主键，与 `/product/:productId` 路由参数一致；
 * 金额字段同时有分与元，优先用 `priceYuan`，缺失时用 `fmtCent(priceCent)` 兜底。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

export interface FavoriteVO {
  productId: string;
  productName: string;
  mainImage?: string | null;
  priceCent: number | string;
  priceYuan?: string | null;
  /** 是否仍在售：下架商品保留展示但灰显 */
  onShelf: boolean;
  favoritedAt?: number | string | null;
}

export interface FavoriteStatusVO {
  favorited: boolean;
  favoriteCount: number | string;
}

export const favoriteApi = {
  list: (query: PageQuery) => http.get<PageResult<FavoriteVO>>('/favorites', query),
  status: (productId: number | string) =>
    http.get<FavoriteStatusVO>('/favorites/status', { productId }),
  /** 收藏（幂等：重复收藏后端不报错） */
  add: (productId: number | string) => http.post<boolean>(`/favorites/${productId}`),
  /** 取消收藏（幂等：未收藏返回 false） */
  remove: (productId: number | string) => http.delete<boolean>(`/favorites/${productId}`),
};
