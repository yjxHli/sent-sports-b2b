/**
 * 客服 API（P6-20，对应 AdminChatController）。
 * 会话实时消息走 /ws/chat，这里只负责列表、接待、转接、结束与在线状态。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult, ChatSessionVO, ChatMessageVO } from '@sent/shared';

export interface PresenceVO {
  staffId: number;
  presence: string;
  releasedSessions: number;
}

export interface SessionActionVO {
  sessionId: number;
  status: string;
  staffId: number | null;
}

export interface CloseVO {
  sessionId: number;
  status: string;
}

export interface WorkloadVO {
  staffId: number;
  staffName?: string;
  activeSessions: number;
  presence?: string;
}

export const chatApi = {
  sessions: (query: PageQuery & { status?: string }) =>
    http.get<PageResult<ChatSessionVO>>('/admin/chat/sessions', query),
  workload: () => http.get<WorkloadVO[]>('/admin/chat/workload'),
  presence: (staffId: number, presence: string) =>
    http.post<PresenceVO>('/admin/chat/presence', { staffId, presence }),
  accept: (id: number, staffId: number) =>
    http.post<SessionActionVO>(`/admin/chat/sessions/${id}/accept`, { staffId }),
  transfer: (id: number, toStaffId: number, reason: string) =>
    http.post<SessionActionVO>(`/admin/chat/sessions/${id}/transfer`, { toStaffId, reason }),
  close: (id: number, reason: string) =>
    http.post<CloseVO>(`/admin/chat/sessions/${id}/close`, { reason }),

  /** 用户端会话历史（客服工作台三栏的消息区） */
  history: (sessionId: number, seq?: number) =>
    http.get<ChatMessageVO[]>(`/chat/sessions/${sessionId}/messages`, seq === undefined ? {} : { seq }),
};
