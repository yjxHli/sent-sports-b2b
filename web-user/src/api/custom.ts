/**
 * 定制 API（用户端，对齐 CustomController P4-25）。
 * 上传图稿仅传元数据（fileId/fileName/fileSize）；真实文件上传端点后端 P4 未提供，前端生成占位 fileId。
 * 我的定制列表（/api/custom-orders/my，收口阶段补齐）联查订单状态与图稿版本。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

export interface CustomVO {
  customOrderId: string;
  status: string;
  version: number;
  rejectCount: number;
  rejectReason?: string | null;
  reviewDeadlineMillis: number;
  /**
   * 图稿上传截止时刻（毫秒，0 = 不适用）。
   * 仅「待提交图稿 + 一稿未传 + 订单已支付」时非 0：支付后 72 小时未上传，
   * 系统会自动取消订单并全额退款，详情页据此渲染倒计时。
   */
  draftDeadlineMillis?: number | string | null;
  /** 关联订单号（可跳订单详情）；查不到订单时为 null */
  orderNo?: string | null;
  orderStatus?: string | null;
  serviceType?: string | null;
  printSide?: string | null;
  qty?: number | null;
  /** 定制费（分）。后端把 Long 序列化成字符串，用前必须 Number() */
  feeCent?: number | string | null;
  expectShipDateMillis?: number | string | null;
  createdAtMillis?: number | string | null;
  drafts?: CustomDraftItemVO[];
  reviewLogs?: CustomReviewLogVO[];
}

/** 图稿版本项：fileId 直接拼 /api/files/{fileId} 预览 */
export interface CustomDraftItemVO {
  version: number;
  fileId: string;
  fileName?: string | null;
  fileSize?: number | string | null;
  uploadedAtMillis?: number | string | null;
}

/** 审图记录项：result = APPROVED / REJECTED */
export interface CustomReviewLogVO {
  draftVersion: number;
  result: string;
  reasonCode?: string | null;
  remark?: string | null;
  reviewedAtMillis?: number | string | null;
}

export interface DraftVO {
  customOrderId: string;
  version: number;
  status: string;
  reviewDeadlineMillis: number;
}

/** 我的定制单列表项：联带订单状态与最新图稿版本（收口阶段补齐） */
export interface MyCustomOrderVO {
  customOrderId: string;
  status: string;
  orderStatus: string;
  version: number;
  rejectCount: number;
  reviewDeadlineMillis: number;
}

/**
 * 订单详情页的定制单条目（`GET /api/custom-orders/by-order/{orderNo}`）。
 * 一张订单可能有多行定制件 → 数组；订单无定制件时为空数组。
 */
export interface OrderCustomVO {
  customOrderId: string;
  status: string;
  version: number;
  rejectCount: number;
  qty: number;
  /** 定制费（分）。JacksonConfig 把 Long 序列化成字符串，用前必须 Number() */
  feeCent: number | string;
  reviewDeadlineMillis: number | string;
}

/** 批量删除结果（与订单侧同构）：`id` 为定制单号，`reason` 可直接展示 */
export interface CustomBatchDeleteResult {
  deleted: number;
  skipped: { id: string; reason: string }[];
}

/** 定制四类筛选桶（2026-11-28）：与后端 `CustomStatusBucket` 同名，列表与角标共用一份口径 */
export type CustomBucket = 'TODO' | 'REVIEW' | 'PRODUCING' | 'DONE';

/** 「我的定制」四类筛选卡角标：total 含「已取消」，所以可能大于四桶之和 */
export interface MyCustomSummaryVO {
  todo: number | string;
  review: number | string;
  producing: number | string;
  done: number | string;
  total: number | string;
}

export const customApi = {
  detail: (customOrderId: string) =>
    http.get<CustomVO>(`/customize/orders/${customOrderId}`),
  uploadDraft: (customOrderId: string, cmd: { fileId: string; fileName: string; fileSize: number }) =>
    http.post<DraftVO>(`/customize/orders/${customOrderId}/drafts`, cmd),
  my: (query: PageQuery & { bucket?: CustomBucket | '' }) =>
    http.get<PageResult<MyCustomOrderVO>>('/custom-orders/my', query),
  /** 四类角标：计数与列表筛选同源（后端同一份桶定义），点卡片筛出的单数 = 卡片上的数字 */
  mySummary: () => http.get<MyCustomSummaryVO>('/custom-orders/my/summary'),
  /**
   * 多选删除定制单：只有终态（已发货 / 已取消）会真的删，
   * 待提交图稿 / 待审核 / 生产中的单后端逐条回「还在进行中」。
   */
  batchRemove: (customNos: string[]) =>
    http.post<CustomBatchDeleteResult>('/custom-orders/batch-delete', { ids: customNos }),
  /** 订单详情页：按订单号取该单的定制单（订单无定制件返回空数组） */
  byOrder: (orderNo: string) =>
    http.get<OrderCustomVO[]>(`/custom-orders/by-order/${encodeURIComponent(orderNo)}`),
};

/** 商品页选图后暂存的图稿（下单成功再补交到刚生成的定制单，避免用户白选） */
export interface PendingDraft {
  fileId: string;
  fileName: string;
  fileSize: number;
}

const PENDING_DRAFT_KEY = 'sent_user.pending_custom_draft';

export function savePendingDraft(draft: PendingDraft): void {
  try {
    localStorage.setItem(PENDING_DRAFT_KEY, JSON.stringify(draft));
  } catch {
    /* 隐私模式下写不进去也不影响主流程 */
  }
}

export function readPendingDraft(): PendingDraft | null {
  try {
    const raw = localStorage.getItem(PENDING_DRAFT_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PendingDraft;
    return parsed?.fileId ? parsed : null;
  } catch {
    return null;
  }
}

export function clearPendingDraft(): void {
  try {
    localStorage.removeItem(PENDING_DRAFT_KEY);
  } catch {
    /* ignore */
  }
}

