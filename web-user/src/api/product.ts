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
  ladderPriceCent?: number | null;
  ladderPriceYuan?: string | null;
}

export interface CatalogSkuVO {
  skuId: string;
  /** 库内真实 SKU 编号，规格为空时的兜底展示 */
  skuCode?: string;
  spec: string;
  stockQty: number;
  onShelf: boolean;
  price: CatalogPriceVO;
  /** 款式图 fileId（V54）：选中规格后联动主图 */
  imageFileId?: string | null;
  /**
   * 该规格自身的秒杀价（V54）。无生效活动、或活动没挂这个规格时为 null ——
   * 不能拿活动内其他规格的秒杀价顶替，否则详情价与结算价会打架。
   */
  flashPriceCent?: number | null;
  flashPriceYuan?: string | null;
}

/**
 * 秒杀横幅快照（V54）：服务端只在「有已发布且时间窗覆盖当前时刻的活动」时返回，
 * 否则为 null，详情页据此整块不渲染（不是隐藏）。
 */
export interface CatalogFlashSaleVO {
  activityId?: string | null;
  title?: string | null;
  flashPriceCent: number;
  flashPriceYuan: string;
  originPriceCent: number;
  originPriceYuan: string;
  startAt?: string | null;
  endAt?: string | null;
}

/** 商品参数键值对（V54）：来源 product.params_json */
export interface CatalogParamVO {
  label: string;
  value: string;
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
  /* ---- V54 富内容：全部可能为空，前端据此渲染真实空态 ---- */
  subtitle?: string | null;
  brand?: string | null;
  /** 图集（≤8，第一张默认主图） */
  images?: string[];
  /** 详情长图（≤20，按序渲染） */
  detailImages?: string[];
  /** 规格参数（前 4 条在详情页放大成宫格） */
  params?: CatalogParamVO[];
  /** 服务承诺标签 */
  serviceTags?: string[];
  /** product.detail_html，纯文本详情 */
  description?: string | null;
  flashSale?: CatalogFlashSaleVO | null;
  canCustomize?: boolean;
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
  /** 首页搜索联想：按标题模糊匹配，返回商品名 + 分类，点击跳对应分类结果页 */
  suggest: (keyword: string, limit = 10) =>
    http.get<Array<{ productId: string; name: string; categoryId: number | null; categoryName?: string | null }>>(
      '/products/suggest', { keyword, limit }),
  detail: (productId: string) =>
    http.get<CatalogProductVO>(`/products/${productId}`),
  skus: (productId: string) =>
    http.get<CatalogSkuVO[]>(`/products/${productId}/skus`),
  categories: () =>
    http.get<CategoryVO[]>('/categories'),
};
