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
}

/** 排队状态：status=WAITING 时 position 为排队位置（1 开始），已接入为 0。 */
export interface QueueVO {
  sessionId: number;
  status: string;
  position: number;
  staffId?: number | null;
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
  createSession: (customerId: number, vip: boolean) =>
    http.post<SessionVO>('/chat/sessions', { customerId, vip }),
  sendMessage: (
    sessionId: number,
    cmd: { senderType: string; content: string; clientMsgId: string; msgType?: string },
  ) => http.post<MessageVO>(`/chat/sessions/${sessionId}/messages`, cmd),
  history: (sessionId: number, afterSeq = 0) =>
    http.get<MessageVO[]>(`/chat/sessions/${sessionId}/messages`, { afterSeq }),
  reportRead: (sessionId: number, seq: number) =>
    http.post<void>(`/chat/sessions/${sessionId}/read`, { seq }),
  /** 排队位置（WAITING 时轮询）：前方人数 +1，已接入返回 position=0 */
  queue: (sessionId: number) =>
    http.get<QueueVO>(`/chat/sessions/${sessionId}/queue`),
  /** 给刚刚服务的客服打分：SATISFIED / NORMAL / UNSATISFIED；重复提交幂等 */
  rateSatisfaction: (sessionId: number, score: string) =>
    http.post<{ sessionId: number; score: string; messageId: string }>(
      `/chat/sessions/${sessionId}/satisfaction`, { score }),
};
