/**
 * 定制审核 API（P6-17，对应 AdminCustomController / CustomController）。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult, CustomVO, DraftVO } from '@sent/shared';

export interface ReviewVO {
  customOrderId: string;
  status: string;
  lockedVersion: number;
  remainingMillis: number;
}

export interface ProduceVO {
  customOrderId: string;
  status: string;
  startedAtMillis: number;
  finishedAtMillis: number;
}

export interface RejectCmd {
  reasonCode: string;
  remark: string;
}

export interface UploadDraftCmd {
  fileId: string;
  fileName?: string;
  fileSize?: number;
}

/** 图稿版本（custom_draft 真库行）。 */
export interface CustomDraftItemVO {
  version: number;
  fileId: string | null;
  fileName: string | null;
  fileSize: number;
}

/** 审图日志（custom_review_log 真库行）。 */
export interface CustomReviewItemVO {
  draftVersion: number;
  result: string;
  reasonCode: string | null;
  remark: string | null;
  reviewerId: number | null;
}

/** 审核台详情：主状态 + 真库图稿/审图轨迹。 */
export interface CustomDetailVO extends CustomVO {
  lockedVersion?: number;
  draftCount?: number;
  reviewLogCount?: number;
  drafts?: CustomDraftItemVO[];
  reviews?: CustomReviewItemVO[];
}

export const customApi = {
  pending: (query: PageQuery) => http.get<PageResult<CustomVO>>('/admin/custom-orders', query),
  detail: (id: string) => http.get<CustomDetailVO>(`/admin/custom-orders/${id}`),
  pass: (id: string) => http.post<ReviewVO>(`/admin/custom-orders/${id}/pass`),
  reject: (id: string, cmd: RejectCmd) =>
    http.post<ReviewVO>(`/admin/custom-orders/${id}/reject`, cmd),
  production: (id: string) => http.post<ProduceVO>(`/admin/custom-orders/${id}/production`),
  finish: (id: string) => http.post<ProduceVO>(`/admin/custom-orders/${id}/finish`),

  /** 用户端图稿上传（左图右单页面的版本来源） */
  uploadDraft: (id: string, cmd: UploadDraftCmd) =>
    http.post<DraftVO>(`/customize/orders/${id}/drafts`, cmd),
  customDetail: (id: string) => http.get<CustomVO>(`/customize/orders/${id}`),
};
