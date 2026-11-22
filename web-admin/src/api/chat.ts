/**
 * 客服治理 API（管理端，对应 AdminChatController）。
 *
 * 2026-11-20 架构调整：**客服工作台已整体剥离到独立工程 `web-cs`（:5175）**。
 * 接待类动作（在线状态 / 接待 / 转接 / 结束 / 实时消息 / 本次小结 / 顾客订单）
 * 不再由管理端发起，相关方法与 VO 已从本文件删除——否则「两边都能接待」会出现
 * 坐席状态与并发上限两套真源，还会让管理端继续背一份 WebSocket 长连接。
 *
 * 管理端只保留「治理」视角（客服审查 / 客服指标）用到的接口：
 * - `auditSessions` / `auditStaff`：审查列表与客服下拉
 * - `transcript`：单会话转录详情（审查弹窗复用）
 * - `metrics`：按客服聚合的服务指标
 * - `batchDeleteAuditSessions`：审查记录批量软删（需 X-Confirm-Token）
 */
import { http, type SentRequestConfig } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

/* ---------- 会话转录（审查弹窗） ---------- */

/** 单会话状态（转录详情头部用） */
export interface ChatSessionDetailVO {
  sessionId: number;
  status: string;
  staffId: number | null;
  customerId: number;
  startedAtMillis: number;
  endedAtMillis: number;
  closeReason?: string | null;
}

export interface ChatNoteVO {
  sessionId: number;
  summary: string;
  category: string;
  info: string;
  result: string;
  solution: string;
  staffId: number;
  updatedAtMillis: number;
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

/* ---------- 客服审查 ---------- */

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

/* ---------- 客服指标 ---------- */

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
  /** 坐席当前状态（不是区间内的历史值）：ONLINE / BUSY / OFFLINE */
  presence: string;
}

export interface ChatMetricsVO {
  from: string;
  to: string;
  rows: ChatMetricsRowVO[];
}

/** 坐席负载行（/admin/chat/workload），指标页用它每 10 秒刷新一次「当前状态」 */
export interface ChatWorkloadVO {
  staffId: number;
  presence: string;
  activeSessions: number;
  maxSessions: number;
}

export const chatApi = {
  transcript: (sessionId: number) =>
    http.get<ChatTranscriptVO>(`/admin/chat/sessions/${sessionId}/transcript`),
  auditSessions: (query: PageQuery & {
    staffId?: number; from?: string; to?: string; result?: string; keyword?: string;
  }) => http.get<PageResult<ChatAuditRowVO>>('/admin/chat/audit/sessions', query),
  auditStaff: () => http.get<ChatAuditStaffVO[]>('/admin/chat/audit/staff'),
  /** 客服指标：今天 / 近 7 天 / 近 30 天 / 自定义区间，按客服聚合；presence 按当前状态过滤 */
  metrics: (query: { staffId?: number; from?: string; to?: string; presence?: string }) =>
    http.get<ChatMetricsVO>('/admin/chat/metrics', query),
  /** 各坐席当前状态与负载（实时刷新的唯一来源） */
  workload: () => http.get<ChatWorkloadVO[]>('/admin/chat/workload'),
  /** 客服审查批量软删：与发票一致，config 必须带 openConfirm 换来的 X-Confirm-Token */
  batchDeleteAuditSessions: (ids: string[], config?: SentRequestConfig) =>
    http.post<{ deleted: number }>('/admin/chat/audit/sessions/batch-delete', { ids }, config),
};
