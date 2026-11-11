/**
 * 用户端客服 WebSocket 连接封装（收口阶段）。
 *
 * 协议对齐 server/chat/ws/WsEnvelope.java 与 ChatWebSocketHandler：
 * - 握手 `/ws/chat?token=<user 端 accessToken>`，后端收口后已接受 aud=user 并注册到客户维度。
 * - 报文 { type, sessionId, seq, payload, timestamp }，type ∈ MSG/ACK/READ/QUEUE。
 * - 发送 MSG 时 payload 为 JSON 字符串 { clientMsgId, content }，clientMsgId 是后端
 *   `uk_msg_client` 幂等键；落库 SenderType 由后端按连接身份写 CUSTOMER，不信任报文里的 senderType。
 * - 客服回复由后端广播到本客户连接；ACK 携带 ACCEPTED / DUPLICATE 或错误码。
 *
 * 页面不直接持有 WebSocket：连接、指数退避重连、帧解析、幂等键生成都收在本 composable。
 */
import { onUnmounted, ref, type Ref } from 'vue';
import { getAccessToken } from '@/utils/auth';
import { newIdempotencyKey } from '@/utils/idempotent';

export type WsType = 'MSG' | 'ACK' | 'READ' | 'QUEUE';

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
  onDisconnected?: () => void;
}

export interface ChatSocket {
  connected: Ref<boolean>;
  connect: () => void;
  close: () => void;
  /** 返回 clientMsgId；未连接时返回 null，调用方据此走 REST 兜底 */
  send: (sessionId: number, content: string) => string | null;
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
      });
      return;
    }
    if (envelope.type === 'QUEUE' && body.event === 'CONNECTED') {
      options.onConnected?.(Number(body.online ?? body.onlineCount ?? 0));
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
      options.onDisconnected?.();
    };
    socket.onclose = () => {
      connected.value = false;
      socket = null;
      options.onDisconnected?.();
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

  function send(sessionId: number, content: string): string | null {
    if (!socket || socket.readyState !== WebSocket.OPEN) return null;
    const clientMsgId = `ws_${newIdempotencyKey('chat')}`;
    localSeq += 1;
    const envelope: WsEnvelope = {
      type: 'MSG',
      sessionId,
      seq: localSeq,
      payload: JSON.stringify({ clientMsgId, content, senderType: 'CUSTOMER' }),
      timestamp: Date.now(),
    };
    socket.send(JSON.stringify(envelope));
    return clientMsgId;
  }

  onUnmounted(close);
  return { connected, connect, close, send };
}
