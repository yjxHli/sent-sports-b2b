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

/** 评价中心（2026-11-28）：顶部三项统计 + 我写过的评价 */
export interface MyReviewVO {
  reviewId: string;
  productId: string;
  productTitle: string;
  mainImage?: string | null;
  rating: number;
  content: string;
  images: string[];
  anonymous: boolean;
  /** NORMAL / HIDDEN：被运营隐藏的评价仍属于「我写过的」，卡片上要标记 */
  status: string;
  viewCount: number | string;
  likeCount: number | string;
  createdAt: number | string;
}

export interface MyReviewSummaryVO {
  reviewCount: number | string;
  viewCount: number | string;
  likeCount: number | string;
}

export interface MyReviewPageVO {
  summary: MyReviewSummaryVO;
  list: MyReviewVO[];
  total: number | string;
  page: number | string;
  size: number | string;
  pages: number | string;
}

/** 评价中心排序（服务端白名单，非法值后端按最新处理） */
export type ReviewSort = 'latest' | 'earliest' | 'comprehensive';

/** 待评价的一条：已完成订单里尚未评价的商品 */
export interface MyPendingReviewVO {
  orderNo: string;
  productId: string;
  productTitle: string;
  mainImage?: string | null;
  qty: number | string;
  paidCent: number | string;
  paidYuan?: string | null;
  completedAt: number | string;
}

export interface MyPendingPageVO {
  list: MyPendingReviewVO[];
  total: number | string;
  page: number | string;
  size: number | string;
  pages: number | string;
}

export const reviewApi = {
  list: (productId: number | string, query: PageQuery) =>
    http.get<ReviewPageVO>(`/products/${productId}/reviews`, query),
  eligibility: (productId: number | string) =>
    http.get<ReviewEligibilityVO>(`/products/${productId}/review-eligibility`),
  submit: (payload: CreateReviewPayload) => http.post<ReviewVO>('/reviews', payload),
  /**
   * 评价中心「已评价」：已写评论 / 浏览数 / 获赞数 + 我的评价列表（必须登录）。
   * `sort` 走服务端白名单：latest / earliest / comprehensive。
   */
  mine: (query: PageQuery & { sort?: ReviewSort }) => http.get<MyReviewPageVO>('/me/reviews', query),
  /** 评价中心「待评价」：已完成订单里尚未评价的商品（与详情页「去评价」资格同源） */
  pending: (query: PageQuery) => http.get<MyPendingPageVO>('/me/reviews/pending', query),
  /** 删除我自己的一条评价（删不到行后端回 404） */
  remove: (reviewId: number | string) => http.delete<void>(`/me/reviews/${reviewId}`),
};
