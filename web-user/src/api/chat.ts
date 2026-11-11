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

export interface MessageVO {
  messageId: string;
  sessionId: number;
  senderType: string;
  content: string;
  clientMsgId?: string | null;
  seq: number;
  createdAtMillis: number;
}

export const chatApi = {
  createSession: (customerId: number, vip: boolean) =>
    http.post<SessionVO>('/chat/sessions', { customerId, vip }),
  sendMessage: (sessionId: number, cmd: { senderType: string; content: string; clientMsgId: string }) =>
    http.post<MessageVO>(`/chat/sessions/${sessionId}/messages`, cmd),
  history: (sessionId: number, afterSeq = 0) =>
    http.get<MessageVO[]>(`/chat/sessions/${sessionId}/messages`, { afterSeq }),
  reportRead: (sessionId: number, seq: number) =>
    http.post<void>(`/chat/sessions/${sessionId}/read`, { seq }),
};
