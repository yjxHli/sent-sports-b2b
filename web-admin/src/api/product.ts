/**
 * 商品 API（P6-14，对应 AdminProductController / CatalogController）。
 * 金额一律「分」，字段名带 Cent，页面展示走 fmtCent。
 */
import { http, type SentRequestConfig } from '@/utils/http';
import type {
  PageQuery, PageResult, ProductVO, CategoryVO, CustomerPriceVO,
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
  /** 款式图 fileId（V54） */
  imageFileId?: string | null;
}

/** 商品参数键值对（V54） */
export interface ParamItem {
  label: string;
  value?: string | null;
}

/**
 * 商品表单（V54）。
 * 富内容三态语义：字段为 `null`/`undefined` 表示不修改；传空数组表示清空。
 */
export interface ProductForm {
  name: string;
  categoryId: number;
  description?: string;
  skus?: SkuForm[];
  subtitle?: string | null;
  brand?: string | null;
  canCustomize?: boolean | null;
  mainImage?: string | null;
  images?: string[];
  detailImages?: string[];
  params?: ParamItem[];
  serviceTags?: string[];
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
  /** 款式图 fileId（V54） */
  imageFileId?: string | null;
}

/** 秒杀快照（V54）：无生效活动时为 null */
export interface FlashSaleVO {
  activityId?: string | null;
  title?: string | null;
  flashPriceCent: number;
  flashPriceYuan: string;
  originPriceCent: number;
  originPriceYuan: string;
  startAt?: string | null;
  endAt?: string | null;
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
  subtitle?: string | null;
  brand?: string | null;
  images?: string[];
  detailImages?: string[];
  params?: ParamItem[];
  serviceTags?: string[];
  flashSale?: FlashSaleVO | null;
  /** 是否支持 UV 定制（V54 补齐读侧，供编辑页回显） */
  canCustomize?: boolean;
}

/* ---------------- 秒杀活动（V54） ---------------- */

export interface FlashSaleItem {
  skuId: number;
  flashPriceCent: number;
}

export interface FlashSaleForm {
  title: string;
  startAt: string;
  endAt: string;
  remark?: string | null;
  items: FlashSaleItem[];
}

export interface FlashSaleItemVO {
  skuId: string;
  skuCode?: string | null;
  productName?: string | null;
  spec?: string | null;
  retailPriceCent: number;
  floorPriceCent: number;
  flashPriceCent: number;
}

/** displayStatus：DRAFT / PENDING / ONGOING / ENDED / OFFLINE（后三者按时间窗现算） */
export interface FlashSaleActivityVO {
  activityId: string;
  activityNo: string;
  title: string;
  status: string;
  displayStatus: string;
  startAt: string;
  endAt: string;
  remark?: string | null;
  itemCount: number;
  items?: FlashSaleItemVO[];
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

  /**
   * 公开商品目录（带 SKU 明细）：券的「适用商品」选择器用它，
   * 管理端列表接口不含 SKU 明细，选不到「指定规格」。
   */
  catalog: (query: { keyword?: string } & PageQuery) =>
    http.get<PageResult<CatalogProductVO>>('/products', query),
  catalogSkus: (productId: number | string) =>
    http.get<CatalogSkuVO[]>(`/products/${productId}/skus`),

  /* ---------------- 秒杀活动（V54） ---------------- */

  flashSales: (query: PageQuery & { keyword?: string; status?: string }) =>
    http.get<PageResult<FlashSaleActivityVO>>('/admin/flash-sales', query),
  flashSaleDetail: (id: number | string) =>
    http.get<FlashSaleActivityVO>(`/admin/flash-sales/${id}`),
  createFlashSale: (form: FlashSaleForm) =>
    http.post<FlashSaleActivityVO>('/admin/flash-sales', form),
  updateFlashSale: (id: number | string, form: FlashSaleForm) =>
    http.put<FlashSaleActivityVO>(`/admin/flash-sales/${id}`, form),
  /** 发布（active=true）/ 下线（active=false） */
  setFlashSaleStatus: (id: number | string, active: boolean) =>
    http.post<FlashSaleActivityVO>(`/admin/flash-sales/${id}/status?active=${active}`),
  removeFlashSale: (id: number | string) =>
    http.delete<void>(`/admin/flash-sales/${id}`),
};
