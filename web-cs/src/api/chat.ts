/**
 * 客服 API（P6-20，对应 AdminChatController）。
 * 会话实时消息走 /ws/chat，这里只负责列表、接待、转接、结束与在线状态。
 */
import { http, type SentRequestConfig } from '@/utils/http';
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
  /** 进线客户昵称（2026-11-25）：未设置为 null，前端兜底成「客户 {id}」 */
  customerName?: string | null;
  /** 进线客户头像 fileId；无头像为 null，前端用姓名首字兜底 */
  customerAvatarFileId?: string | null;
}

/** 工作台「今日完成接待数」：服务端按本地自然日统计，跨零点自动归零 */
export interface TodayStatsVO {
  staffId: number;
  date: string;
  closedCount: number;
  /** 当日有效评价数（一般/不满意也算有效），Long 由 Jackson 序列化成字符串 */
  ratedCount?: number;
  satisfiedCount?: number;
  /** 满意率百分比；-1 = 当日还没有评价，前端显示 -- */
  ratePercent?: number;
}

export interface OpenSessionsVO {
  active: OpenSessionRow[];
  waiting: OpenSessionRow[];
}

/** 单会话状态（工作台 Tab 置灰用） */
export interface ChatSessionDetailVO {
  sessionId: number;
  status: string;
  staffId: number | null;
  customerId: number;
  startedAtMillis: number;
  endedAtMillis: number;
  closeReason?: string | null;
}

/** 服务工单记录（V61）：原有小结字段 + 工单扩展字段 + 服务端带入的只读客户基础信息。 */
export interface ChatNoteVO {
  sessionId: number;
  summary: string;
  category: string;
  info: string;
  result: string;
  solution: string;
  staffId: number;
  updatedAtMillis: number;
  subCategory?: string | null;
  customerDemand?: string | null;
  verifiedInfo?: string | null;
  needFollowup?: boolean;
  followupTimeMillis?: number | null;
  /** 回访联系方式：客服手填（V65），不再回落到客户登录手机号。 */
  followupContact?: string | null;
  attachmentsJson?: string | null;
  /** DRAFT 暂存 / SUBMITTED 已提交；只有已提交的工单进管理端客服审查（V65）。 */
  submitStatus?: 'DRAFT' | 'SUBMITTED' | null;
  submittedAtMillis?: number | null;
  customerNickname?: string | null;
  customerPhone?: string | null;
  customerEmail?: string | null;
  consultedAtMillis?: number;
}

export interface ChatMessageLineVO {
  seq: number;
  senderType: string;
  msgType: string;
  content: string;
  createdAtMillis: number;
}

export interface ChatTranscriptVO {
  session: ChatSessionDetailVO;
  messages: ChatMessageLineVO[];
  note: ChatNoteVO | null;
}

export interface ChatHistoryRowVO {
  sessionId: number;
  staffId: number | null;
  staffName?: string | null;
  startedAtMillis: number;
  endedAtMillis: number;
  closeReason?: string | null;
  result?: string | null;
  messageCount: number;
}

export interface ChatAuditRowVO {
  sessionId: number;
  customerId: number;
  staffId: number;
  staffName?: string | null;
  startedAtMillis: number;
  endedAtMillis: number;
  summary?: string | null;
  result?: string | null;
  noteStatus: string;
}

export interface ChatAuditStaffVO {
  staffId: number;
  name: string;
}

/** 客服指标行（后端 ChatMetricsDtos.Row；Long 序列化成字符串，前端统一 Number()） */
export interface ChatMetricsRowVO {
  staffId: number;
  staffName: string;
  closedCount: number;
  ratedCount: number;
  satisfiedCount: number;
  /** -1 = 区间内无有效评价，显示 -- */
  satisfactionRate: number;
  resolvedCount: number;
  /** -1 = 区间内无已结束会话，显示 -- */
  resolutionRate: number;
  avgDurationSeconds: number;
}

export interface ChatMetricsVO {
  from: string;
  to: string;
  rows: ChatMetricsRowVO[];
}

export interface SaveNotePayload {
  staffId: number;
  summary: string;
  category: string;
  info: string;
  result: string;
  solution: string;
  subCategory?: string | null;
  customerDemand?: string | null;
  verifiedInfo?: string | null;
  needFollowup?: boolean;
  followupTime?: string | null;
  /** 回访联系方式：客服手填（V65）。 */
  followupContact?: string | null;
  attachmentsJson?: string | null;
  /** true = 提交工单（校验必填并进客服审查）；false = 暂时保存草稿。 */
  submit?: boolean;
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
  close: (id: number, reason: string) =>
    http.post<CloseVO>(`/admin/chat/sessions/${id}/close`, { reason }),
  /** 客服主动发「请评价本次服务」卡片；同会话幂等 */
  sendSatisfaction: (id: number) =>
    http.post<{ messageId: string }>(`/admin/chat/sessions/${id}/satisfaction`, {}),

  /** 用户端会话历史（客服工作台三栏的消息区） */
  history: (sessionId: number, seq?: number) =>
    http.get<ChatMessageVO[]>(`/chat/sessions/${sessionId}/messages`, seq === undefined ? {} : { seq }),

  /* ---------- 会话小结 / 历史服务 / 客服审查 ---------- */
  sessionDetail: (sessionId: number) =>
    http.get<ChatSessionDetailVO>(`/admin/chat/sessions/${sessionId}`),
  transcript: (sessionId: number) =>
    http.get<ChatTranscriptVO>(`/admin/chat/sessions/${sessionId}/transcript`),
  customerHistory: (customerId: number, excludeSessionId = 0, limit = 10) =>
    http.get<ChatHistoryRowVO[]>(`/admin/chat/customers/${customerId}/sessions`,
      { excludeSessionId, limit }),
  note: (sessionId: number) =>
    http.get<ChatNoteVO | null>(`/admin/chat/sessions/${sessionId}/note`),
  saveNote: (sessionId: number, payload: SaveNotePayload) =>
    http.put<ChatNoteVO>(`/admin/chat/sessions/${sessionId}/note`, payload),
  auditSessions: (query: PageQuery & {
    staffId?: number; from?: string; to?: string; result?: string; keyword?: string;
  }) => http.get<PageResult<ChatAuditRowVO>>('/admin/chat/audit/sessions', query),
  auditStaff: () => http.get<ChatAuditStaffVO[]>('/admin/chat/audit/staff'),
  /** 客服指标：今天 / 近 7 天 / 近 30 天 / 自定义区间，按客服聚合 */
  metrics: (query: { staffId?: number; from?: string; to?: string }) =>
    http.get<ChatMetricsVO>('/admin/chat/metrics', query),
  /** 客服审查批量软删：与发票一致，config 必须带 openConfirm 换来的 X-Confirm-Token */
  batchDeleteAuditSessions: (ids: string[], config?: SentRequestConfig) =>
    http.post<{ deleted: number }>('/admin/chat/audit/sessions/batch-delete', { ids }, config),
};
