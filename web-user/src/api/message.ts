/**
 * 站内信 API（商城端消息中心）。
 *
 * 与 `api/chat.ts`（在线客服）完全独立：客服是实时会话，这里是异步通知。
 * `id` 由后端 Long 序列化成字符串，使用前一律 Number() 归一。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

export interface MessageVO {
  id: number | string;
  /** ORDER 订单 / PROMO 优惠 / SYSTEM 系统 */
  msgType: string;
  title: string;
  content: string;
  bizType?: string | null;
  bizNo?: string | null;
  read: boolean;
  /** 毫秒 */
  createdAt?: number | string | null;
}

export interface UnreadCountVO {
  unreadCount: number | string;
}

export const messageApi = {
  list: (query: PageQuery & { type?: string | null }) =>
    http.get<PageResult<MessageVO>>('/messages', query),
  unreadCount: () => http.get<UnreadCountVO>('/messages/unread-count'),
  markRead: (id: number) => http.post<void>(`/messages/${id}/read`),
  markAllRead: () => http.post<number>('/messages/read-all'),
  /** 删除单条（只能删自己的，越权后端按 404 处理） */
  remove: (id: number) => http.delete<void>(`/messages/${id}`),
  /** 一键清空全部站内信，返回实际删除条数 */
  clearAll: () => http.delete<number>('/messages'),
};
