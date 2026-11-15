/**
 * 客服 WebSocket 连接封装（P6-42）。
 *
 * 协议严格对齐 server/chat/ws/WsEnvelope.java 与 ChatWebSocketHandler：
 * - 握手 `/ws/chat?token=<accessToken>`；后端收口后同时接受 aud=admin（客服维度）与
 *   aud=user（客户维度），本端使用 admin 令牌；开发态由 vite 代理 /ws 转发。
 * - 报文 { type, sessionId, seq, payload, timestamp }，type ∈ MSG/ACK/READ/QUEUE/ASSIGNED/TYPING。
 * - TYPING 是「对方正在输入」提示：只转发不落库，页面上显示几秒后自动消失。
 * - 发送 MSG 时 payload 为 JSON 字符串 { clientMsgId, content }，clientMsgId 是后端
 *   `uk_msg_client` 幂等键；ACK 携带 ACCEPTED / DUPLICATE 或错误码。
 *
 * 页面不直接持有 WebSocket：连接、指数退避重连、帧解析、幂等键生成都收在本 composable，
 * 用户端后续可复用同一份逻辑（只换 token 来源与 senderType）。
 */
import { onUnmounted, ref, type Ref } from 'vue';
import { getAccessToken } from '@/utils/auth';
import { newIdempotencyKey } from '@/utils/idempotent';

export type WsType = 'MSG' | 'ACK' | 'READ' | 'QUEUE' | 'ASSIGNED' | 'TYPING';

export interface WsEnvelope {
  type: WsType;
  sessionId: number;
  seq: number;
  payload: string;
  timestamp: number;
}

export interface IncomingMessage {
  sessionId: number;
  seq: number;
  clientMsgId: string;
  content: string;
  senderType: string;
  /** TEXT / IMAGE；IMAGE 时 content 是 fileId，页面拼 fileApi.url() 渲染 */
  msgType?: string;
  self?: boolean;
}

export interface IncomingAck {
  sessionId: number;
  seq: number;
  event: string;
  clientMsgId?: string;
  code?: number;
  reason?: string;
}

export interface ChatSocketOptions {
  onMessage: (message: IncomingMessage) => void;
  onAck: (ack: IncomingAck) => void;
  onConnected?: (onlineCount: number) => void;
  /** 对方（客户）正在输入：page 侧起 3s 定时器自行淡出 */
  onTyping?: (sessionId: number) => void;
}

export interface ChatSocket {
  connected: Ref<boolean>;
  connect: () => void;
  close: () => void;
  /** 返回 clientMsgId；未连接时返回 null，调用方据此提示。IMAGE 时 content 传 fileId */
  send: (sessionId: number, content: string, msgType?: string) => string | null;
  /** 发送「我正在输入」提示；未连接时静默丢弃（弱语义，不重试） */
  sendTyping: (sessionId: number) => void;
}

function socketUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const token = getAccessToken();
  return `${protocol}//${window.location.host}/ws/chat?token=${encodeURIComponent(token)}`;
}

function parsePayload(raw: string): Record<string, unknown> {
  try {
    const value: unknown = JSON.parse(raw);
    return value && typeof value === 'object' ? value as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

export function useChatSocket(options: ChatSocketOptions): ChatSocket {
  const connected = ref(false);
  let socket: WebSocket | null = null;
  let retry = 0;
  let localSeq = 0;
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  let stopped = false;

  function handleEnvelope(raw: string): void {
    let envelope: WsEnvelope;
    try {
      envelope = JSON.parse(raw) as WsEnvelope;
    } catch {
      return;
    }
    const body = parsePayload(envelope.payload ?? '');
    if (envelope.type === 'MSG') {
      options.onMessage({
        sessionId: Number(envelope.sessionId),
        seq: Number(envelope.seq),
        clientMsgId: String(body.clientMsgId ?? ''),
        content: String(body.content ?? envelope.payload ?? ''),
        senderType: String(body.senderType ?? 'STAFF'),
        msgType: String(body.msgType ?? 'TEXT'),
      });
      return;
    }
    if (envelope.type === 'QUEUE') {
      // CONNECTED 是握手回执：只用来点亮「已连接 + 在线数」
      if (body.event === 'CONNECTED') {
        options.onConnected?.(Number(body.online ?? body.onlineCount ?? 0));
        return;
      }
      // 未分配（WAITING）会话的客户消息，后端以 QUEUE 广播（见 ChatWebSocketHandler）。
      // 这里必须当普通消息投给页面——历史缺陷是「QUEUE 非 CONNECTED 一律 return」，
      // 导致客户消息在客服端被整体丢弃，表现为「客服要刷新才看得到客户的新消息」。
      if (!Number(envelope.sessionId)) return;
      options.onMessage({
        sessionId: Number(envelope.sessionId),
        seq: Number(envelope.seq),
        clientMsgId: String(body.clientMsgId ?? ''),
        content: String(body.content ?? envelope.payload ?? ''),
        senderType: String(body.senderType ?? 'CUSTOMER'),
        msgType: String(body.msgType ?? 'TEXT'),
      });
      return;
    }
    if (envelope.type === 'TYPING') {
      options.onTyping?.(Number(envelope.sessionId));
      return;
    }
    if (envelope.type === 'ACK') {
      options.onAck({
        sessionId: Number(envelope.sessionId),
        seq: Number(envelope.seq),
        event: String(body.event ?? (body.code ? 'REJECTED' : 'ACK')),
        clientMsgId: body.clientMsgId === undefined ? undefined : String(body.clientMsgId),
        code: body.code === undefined ? undefined : Number(body.code),
        reason: body.reason === undefined ? undefined : String(body.reason),
      });
    }
  }

  function scheduleReconnect(): void {
    if (stopped) return;
    const delay = Math.min(1000 * 2 ** retry, 10_000);
    retry += 1;
    reconnectTimer = setTimeout(connect, delay);
  }

  function connect(): void {
    if (stopped) return;
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = undefined;
    }
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      return;
    }
    const token = getAccessToken();
    if (!token) return; // 未登录不建连，避免后端 POLICY_VIOLATION 反复关闭

    try {
      socket = new WebSocket(socketUrl());
    } catch {
      scheduleReconnect();
      return;
    }
    socket.onopen = () => {
      connected.value = true;
      retry = 0;
    };
    socket.onmessage = (event: MessageEvent<string>) => handleEnvelope(String(event.data));
    socket.onerror = () => {
      connected.value = false;
    };
    socket.onclose = () => {
      connected.value = false;
      socket = null;
      scheduleReconnect();
    };
  }

  function close(): void {
    stopped = true;
    if (reconnectTimer) clearTimeout(reconnectTimer);
    reconnectTimer = undefined;
    if (socket) {
      socket.onclose = null;
      socket.close();
      socket = null;
    }
    connected.value = false;
  }

  function send(sessionId: number, content: string, msgType = 'TEXT'): string | null {
    if (!socket || socket.readyState !== WebSocket.OPEN) return null;
    const clientMsgId = `ws_${newIdempotencyKey('chat')}`;
    localSeq += 1;
    const envelope: WsEnvelope = {
      type: 'MSG',
      sessionId,
      seq: localSeq,
      payload: JSON.stringify({ clientMsgId, content, senderType: 'STAFF', msgType }),
      timestamp: Date.now(),
    };
    socket.send(JSON.stringify(envelope));
    return clientMsgId;
  }

  function sendTyping(sessionId: number): void {
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    localSeq += 1;
    const envelope: WsEnvelope = {
      type: 'TYPING',
      sessionId,
      seq: localSeq,
      payload: JSON.stringify({ typing: true, senderType: 'STAFF' }),
      timestamp: Date.now(),
    };
    socket.send(JSON.stringify(envelope));
  }

  onUnmounted(close);
  return { connected, connect, close, send, sendTyping };
}
