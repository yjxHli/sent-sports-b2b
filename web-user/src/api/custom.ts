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

export const customApi = {
  detail: (customOrderId: string) =>
    http.get<CustomVO>(`/customize/orders/${customOrderId}`),
  uploadDraft: (customOrderId: string, cmd: { fileId: string; fileName: string; fileSize: number }) =>
    http.post<DraftVO>(`/customize/orders/${customOrderId}/drafts`, cmd),
  my: (query: PageQuery) =>
    http.get<PageResult<MyCustomOrderVO>>('/custom-orders/my', query),
};

