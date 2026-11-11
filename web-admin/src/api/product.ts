/**
 * 商品 API（P6-14，对应 AdminProductController / CatalogController）。
 * 金额一律「分」，字段名带 Cent，页面展示走 fmtCent。
 */
import { http, type SentRequestConfig } from '@/utils/http';
import type {
  PageQuery, PageResult, ProductVO, SkuVO, CategoryVO, CustomerPriceVO,
} from '@sent/shared';

export interface SkuForm {
  /** 已有 SKU 的主键；新增行不传，由后端生成 */
  skuId?: number | string;
  /** 独立 SKU 端点使用；商品编辑保存由路径 id 提供归属 */
  spuId?: number | string;
  skuCode: string;
  spec?: string;
  categoryId: number;
  retailPriceCent: number;
  stock: number;
}

export interface ProductForm {
  name: string;
  categoryId: number;
  description?: string;
  skus?: SkuForm[];
}

/** 与后端 TierDto(minQty, maxQty, rate) 对齐；rate 为相对零售价的百分比 */
export interface LadderTier {
  minQty: number;
  maxQty: number;
  rate: number;
}

export interface LadderForm {
  skuId: number;
  tiers: LadderTier[];
}

export interface LadderVO {
  skuId: number;
  tiers: LadderTier[];
}

/** 与后端 CatalogDtos.PriceVO 对齐。 */
export interface CatalogPriceVO {
  retailPriceCent: number;
  retailPriceYuan: string;
  memberPriceCent?: number | null;
  memberPriceYuan?: string | null;
  agreementPriceCent?: number | null;
  agreementPriceYuan?: string | null;
  /** 阶梯价中的最低绝对价（分）；无折扣为 null */
  ladderPriceCent?: number | null;
  ladderPriceYuan?: string | null;
}

export interface CatalogSkuVO {
  skuId: number | string;
  /** 库内真实 sku_no，供编辑页回显 */
  skuCode?: string;
  spec: string;
  stockQty: number;
  onShelf: boolean;
  price: CatalogPriceVO;
}

export interface CatalogProductVO {
  productId: number | string;
  name: string;
  categoryId?: number | string | null;
  categoryName?: string | null;
  onShelf: boolean;
  skus?: CatalogSkuVO[];
  /** product.detail_html，供编辑页回显 */
  description?: string | null;
}

/** 管理端商品列表行：skuCount 不随折扣变化，discountSkuCount 会变化 */
export interface AdminProductRow extends ProductVO {
  discountSkuCount?: number;
}

export interface CustomerPriceForm {
  skuId: number;
  customerId: number;
  priceCent: number;
  validFromMillis: number;
  validToMillis: number;
}

export interface ProductListQuery extends PageQuery {
  keyword?: string;
  categoryId?: number;
  onShelf?: boolean;
}

export const productApi = {
  list: (query: ProductListQuery) => http.get<PageResult<AdminProductRow>>('/admin/products', query),
  /** config 用于注入 Idempotency-Key 等请求头 */
  create: (form: ProductForm, config?: SentRequestConfig) =>
    http.post<ProductVO>('/admin/products', form, config),
  update: (id: number, form: ProductForm) => http.put<ProductVO>(`/admin/products/${id}`, form),
  /** 详情来自用户端公开目录接口（admin 列表不含 SKU 明细与折扣价） */
  detail: (productId: string | number) => http.get<CatalogProductVO>(`/products/${productId}`),
  remove: (id: number) => http.delete<void>(`/admin/products/${id}`),
  publish: (id: number, onShelf: boolean) =>
    http.post<ProductVO>(`/admin/products/${id}/publish`, { onShelf }),

  addSku: (form: SkuForm) => http.post<SkuVO>('/admin/skus', form),
  updateSku: (id: number, form: SkuForm) => http.put<SkuVO>(`/admin/skus/${id}`, form),
  removeSku: (id: number) => http.delete<void>(`/admin/skus/${id}`),
  configLadder: (id: number, form: LadderForm) =>
    http.put<void>(`/admin/skus/${id}/ladder-prices`, form),
  /** 读取既有阶梯价（折扣率），供编辑页回显与删除折扣 */
  listLadderPrices: (id: number) => http.get<LadderVO>(`/admin/skus/${id}/ladder-prices`),

  listCustomerPrices: (query: { skuId?: number; customerId?: number } = {}) =>
    http.get<CustomerPriceVO[]>('/admin/customer-prices', query),
  upsertCustomerPrice: (form: CustomerPriceForm) =>
    http.post<CustomerPriceVO>('/admin/customer-prices', form),
  removeCustomerPrice: (id: number) => http.delete<void>(`/admin/customer-prices/${id}`),

  /** 分类树来自用户端公开目录接口 */
  categories: () => http.get<CategoryVO[]>('/categories'),
};
