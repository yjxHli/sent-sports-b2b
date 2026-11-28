/**
 * 客服会话 API（用户端，对齐 ChatController P4-31）。
 * 建会话 / 发消息 / 拉历史 / 已读上报；实时收发走 WebSocket（见 composables/useChatSocket）。
 */
import { http } from '@/utils/http';

export interface SessionVO {
  sessionId: number;
  status: string;
  staffId?: number | null;
  reused: boolean;
  /** BOT 机器人接待 / HUMAN 人工（BOT 时 status 虽为 WAITING，但输入框不该按排队禁用） */
  sessionType?: string;
}

/** 排队状态：status=WAITING 时 position 为排队位置（1 开始），已接入为 0。 */
export interface QueueVO {
  sessionId: number;
  status: string;
  position: number;
  staffId?: number | null;
}

/**
 * 历史咨询 / 服务进度用的会话摘要（不含正文）。
 * 正文按需再调 history(sessionId)，避免一次把 2700+ 条历史会话的消息全拉回来。
 */
export interface SessionSummaryVO {
  sessionId: number;
  /** WAITING / ACTIVE / CLOSED */
  status: string;
  staffId?: number | null;
  createdAtMillis: number;
  lastActiveAtMillis: number;
}

export interface MessageVO {
  messageId: string;
  sessionId: number;
  senderType: string;
  /** TEXT / IMAGE；IMAGE 时 content 是 fileId */
  msgType?: string | null;
  content: string;
  clientMsgId?: string | null;
  seq: number;
  createdAtMillis: number;
}

export const chatApi = {
  /** 进入聊天页只查当前未结束会话；没有就返回 null，绝不建空会话给客服看 */
  currentOpen: (customerId: number) =>
    http.get<SessionVO | null>('/chat/sessions/current', { customerId }),
  /** 历史咨询记录 / 服务进度：只出会话摘要，按 id DESC（最新在前） */
  sessions: (customerId: number, limit = 20) =>
    http.get<SessionSummaryVO[]>('/chat/sessions', { customerId, limit }),
  createSession: (customerId: number, vip: boolean, sessionType = 'HUMAN') =>
    http.post<SessionVO>('/chat/sessions', { customerId, vip, sessionType }),
  sendMessage: (
    sessionId: number,
    cmd: { senderType: string; content: string; clientMsgId: string; msgType?: string },
  ) => http.post<MessageVO>(`/chat/sessions/${sessionId}/messages`, cmd),
  history: (sessionId: number, afterSeq = 0) =>
    http.get<MessageVO[]>(`/chat/sessions/${sessionId}/messages`, { afterSeq }),
  /** 排队位置（WAITING 时轮询）：前方人数 +1，已接入返回 position=0 */
  queue: (sessionId: number) =>
    http.get<QueueVO>(`/chat/sessions/${sessionId}/queue`),
  /** 给刚刚服务的客服打分：SATISFIED / NORMAL / UNSATISFIED；重复提交幂等 */
  rateSatisfaction: (sessionId: number, score: string) =>
    http.post<{ sessionId: number; score: string; messageId: string }>(
      `/chat/sessions/${sessionId}/satisfaction`, { score }),
};
