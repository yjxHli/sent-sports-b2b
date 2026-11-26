/**
 * 智能客服话术配置 API（管理端，V60，对应 AdminFaqController）。
 *
 * 口径：
 * - 关键词对外就是「逗号分隔的一串」，归一化（空格 / 中文逗号 / 重复词）由后端做；
 * - 写接口保存后立即刷新知识库快照，用户端下一条消息就用新话术，没有「等几分钟」；
 * - `hitCount` / `id` 都可能被后端序列化成字符串，页面统一 `Number()`。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

export interface FaqVO {
  id: number | string;
  /** 关键词原文（逗号分隔，前端自行拆 Tag） */
  keywords: string;
  replyContent: string;
  /** 命中次数：运营评估话术有效性 */
  hitCount: number | string;
  status: 'ENABLED' | 'DISABLED' | string;
  updatedAtMillis: number | string;
}

export interface FaqQuery extends PageQuery {
  keyword?: string;
  status?: string;
}

export interface FaqForm {
  keywords: string;
  replyContent: string;
  status?: string;
}

export const faqAdminApi = {
  list: (query: FaqQuery) => http.get<PageResult<FaqVO>>('/admin/faq', query),
  create: (form: FaqForm) => http.post<FaqVO>('/admin/faq', form),
  update: (id: number | string, form: FaqForm) => http.put<FaqVO>(`/admin/faq/${id}`, form),
  updateStatus: (id: number | string, status: string) =>
    http.put<FaqVO>(`/admin/faq/${id}/status`, { status }),
  /**
   * 批量删除（V62，软删）：与发票管理同口径，`config` 里带 `X-Confirm-Token`。
   *
   * ids 必须是字符串数组且与确认令牌的 targetId 完全同源（trim → 去空 → 去重 → 字典序升序），
   * 所以由 BatchDeleteButton 统一归一后再传进来，页面不要自己拼。
   */
  batchDelete: (ids: string[], config: { headers: Record<string, string> }) =>
    http.post<{ deleted: number }>('/admin/faq/batch-delete', { ids }, config),
};
