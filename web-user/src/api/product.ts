/**
 * 商品浏览 API（用户端，对齐 CatalogController P4-10）。
 * 金额一律分存储，后端响应已附 *_Yuan 字符串，前端直接用 fmtCent 兜底。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

export interface CatalogPriceVO {
  retailPriceCent: number;
  retailPriceYuan: string;
  memberPriceCent?: number | null;
  memberPriceYuan?: string | null;
  agreementPriceCent?: number | null;
  agreementPriceYuan?: string | null;
}

export interface CatalogSkuVO {
  skuId: string;
  spec: string;
  stockQty: number;
  onShelf: boolean;
  price: CatalogPriceVO;
}

export interface CatalogProductVO {
  productId: string;
  name: string;
  categoryId?: number | null;
  categoryName?: string | null;
  /** 主图地址（演示数据可能为虚构域名，前端会自动降级为占位图） */
  mainImage?: string | null;
  /** 累计销量 */
  salesCount?: number | null;
  onShelf: boolean;
  skus?: CatalogSkuVO[];
}

export interface CategoryVO {
  categoryId: string;
  name: string;
  parentId?: string | null;
  children?: CategoryVO[];
}

export const productApi = {
  list: (query: { categoryId?: number | null; keyword?: string } & PageQuery) =>
    http.get<PageResult<CatalogProductVO>>('/products', query),
  detail: (productId: string) =>
    http.get<CatalogProductVO>(`/products/${productId}`),
  skus: (productId: string) =>
    http.get<CatalogSkuVO[]>(`/products/${productId}/skus`),
  categories: () =>
    http.get<CategoryVO[]>('/categories'),
};
