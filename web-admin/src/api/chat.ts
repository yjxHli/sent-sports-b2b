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
  /** 结束会话后自动补位的排队会话号（无则 null） */
  promotedSessionId?: number | null;
}

export interface WorkloadVO {
  staffId: number;
  staffName?: string;
  activeSessions: number;
  presence?: string;
  maxSessions?: number;
}

/** 工作台左侧两栏的会话行（与后端 OpenSessionsResult.Row 对齐）。 */
export interface OpenSessionRow {
  sessionId: number;
  status: string;
  staffId: number | null;
  customerId: number;
  waitingSinceMillis: number;
  /** 排队位置，1 开始；进行中为 0 */
  queuePosition: number;
}

/** 工作台「今日完成接待数」：服务端按本地自然日统计，跨零点自动归零 */
export interface TodayStatsVO {
  staffId: number;
  date: string;
  closedCount: number;
}

export interface OpenSessionsVO {
  active: OpenSessionRow[];
  waiting: OpenSessionRow[];
}

export const chatApi = {
  sessions: (query: PageQuery & { status?: string }) =>
    http.get<PageResult<ChatSessionVO>>('/admin/chat/sessions', query),
  /** 工作台左侧两栏：我的进行中 + 全员排队中（不分页，服务端限长） */
  openSessions: (staffId: number) =>
    http.get<OpenSessionsVO>('/admin/chat/sessions/open', { staffId }),
  workload: () => http.get<WorkloadVO[]>('/admin/chat/workload'),
  /** 今日完成接待数（顶栏右侧常驻） */
  todayStats: (staffId: number) =>
    http.get<TodayStatsVO>('/admin/chat/stats/today', { staffId }),
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
