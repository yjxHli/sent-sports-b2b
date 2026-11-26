/**
 * 商品评价 API（用户端，对齐 ReviewController / V55）。
 *
 * 口径：
 * - 列表公开可读（未登录也能看评价），提交必须登录；
 * - 后端把 Long 序列化成字符串（JacksonConfig），所有计数字段前端一律 `Number()`；
 * - `images` 是文件服务 fileId 数组，渲染前用 `fileApi.url()` 转访问地址。
 */
import { http } from '@/utils/http';
import type { PageQuery } from '@sent/shared';

export interface ReviewVO {
  reviewId: string;
  /** 已脱敏的昵称（如「张**」；匿名为「匿名用户」） */
  nickname: string;
  /** 首字头像，避免为空评价人再请求一次文件服务 */
  avatarText: string;
  rating: number;
  content: string;
  /** 评价图 fileId 数组 */
  images: string[];
  anonymous: boolean;
  createdAt: number | string;
}

export interface ReviewSummaryVO {
  total: number | string;
  positiveCount: number | string;
  /** 差评条数（rating≤2）；3 星中评不计入好评也不计入差评 */
  negativeCount: number | string;
  /** 好评率（0~100，rating≥4 计为好评） */
  positiveRate: number | string;
  /** 平均分，已按 1 位小数格式化，如 "4.8" */
  avgRating: string;
}

export interface ReviewPageVO {
  summary: ReviewSummaryVO;
  list: ReviewVO[];
  total: number | string;
  page: number | string;
  size: number | string;
  pages: number | string;
}

export interface ReviewEligibilityVO {
  /** 已购买且未评价 */
  eligible: boolean;
  /** 已购买但已评价 */
  reviewed: boolean;
}

export interface CreateReviewPayload {
  productId: number;
  skuId?: number | null;
  rating: number;
  content: string;
  images: string[];
  anonymous: boolean;
}

export const reviewApi = {
  list: (productId: number | string, query: PageQuery) =>
    http.get<ReviewPageVO>(`/products/${productId}/reviews`, query),
  eligibility: (productId: number | string) =>
    http.get<ReviewEligibilityVO>(`/products/${productId}/review-eligibility`),
  submit: (payload: CreateReviewPayload) => http.post<ReviewVO>('/reviews', payload),
};
