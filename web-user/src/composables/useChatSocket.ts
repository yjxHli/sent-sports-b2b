/**
 * 用户端客服 WebSocket 连接封装（收口阶段）。
 *
 * 协议对齐 server/chat/ws/WsEnvelope.java 与 ChatWebSocketHandler：
 * - 握手 `/ws/chat?token=<user 端 accessToken>`，后端收口后已接受 aud=user 并注册到客户维度。
 * - 报文 { type, sessionId, seq, payload, timestamp }，type ∈ MSG/ACK/READ/QUEUE/ASSIGNED/TYPING。
 * - TYPING 是「对方正在输入」提示：只转发不落库，页面上显示几秒后自动消失。
 * - 发送 MSG 时 payload 为 JSON 字符串 { clientMsgId, content }，clientMsgId 是后端
 *   `uk_msg_client` 幂等键；落库 SenderType 由后端按连接身份写 CUSTOMER，不信任报文里的 senderType。
 * - 客服回复由后端广播到本客户连接；ACK 携带 ACCEPTED / DUPLICATE 或错误码。
 *
 * 页面不直接持有 WebSocket：连接、指数退避重连、帧解析、幂等键生成都收在本 composable。
 */
import { onUnmounted, ref, type Ref } from 'vue';
import { getAccessToken } from '@/utils/auth';
import { newIdempotencyKey } from '@/utils/idempotent';

export type WsType = 'MSG' | 'ACK' | 'READ' | 'QUEUE' | 'ASSIGNED' | 'TYPING' | 'CLOSED' | 'SYSTEM';

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
  /** 信封 timestamp（毫秒）：聊天气泡 5 分钟时间分隔用，缺省时页面回落 Date.now() */
  createdAt?: number;
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
  /** 服务端通知「会话已被客服接入」：用户端据此隐藏排队提示、解锁输入 */
  onAssigned?: (sessionId: number, staffId: number | null) => void;
  /** 客服正在输入：页面显示「客服正在回复中」，3s 无新帧自动消失 */
  onTyping?: (sessionId: number) => void;
  /** 会话已结束（客服主动关闭 / 超时）：页面显示「本次会话已结束」，Tab 不会自动消失 */
  onClosed?: (sessionId: number, reason: string) => void;
  /**
   * 系统提示帧（2026-11-24）：机器人回复、转人工提示、人工接入提示。
   * event=TRANSFER 表示客户请求了转人工，前端据此切回「排队中」口径。
   * <p>senderType 决定渲染成什么：BOT 是机器人说的话 → 普通气泡 + 机器人头像；
   * SYSTEM 是旁白（已接入 / 已结束）→ 聊天区正中的浅灰提示。</p>
   */
  onSystem?: (sessionId: number, content: string, seq: number, event: string, senderType: string) => void;
}

export interface ChatSocket {
  connected: Ref<boolean>;
  connect: () => void;
  close: () => void;
  /** 返回 clientMsgId；未连接时返回 null，调用方据此走 REST 兜底。IMAGE 时 content 传 fileId */
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
        createdAt: Number(envelope.timestamp) || Date.now(),
      });
      return;
    }
    if (envelope.type === 'QUEUE' && body.event === 'CONNECTED') {
      options.onConnected?.(Number(body.online ?? body.onlineCount ?? 0));
      return;
    }
    if (envelope.type === 'ASSIGNED') {
      options.onAssigned?.(Number(envelope.sessionId || body.sessionId || 0),
        body.staffId === undefined || body.staffId === null ? null : Number(body.staffId));
      return;
    }
    if (envelope.type === 'TYPING') {
      options.onTyping?.(Number(envelope.sessionId));
      return;
    }
    if (envelope.type === 'CLOSED') {
      options.onClosed?.(Number(envelope.sessionId || body.sessionId || 0),
        String(body.reason ?? ''));
      return;
    }
    if (envelope.type === 'SYSTEM') {
      options.onSystem?.(Number(envelope.sessionId || body.sessionId || 0),
        String(body.content ?? ''), Number(body.seq ?? 0), String(body.event ?? 'SYSTEM_MESSAGE'),
        // 老服务端不发 senderType 时按 SYSTEM 走（居中提示），不会把旁白错认成机器人说话
        String(body.senderType ?? 'SYSTEM'));
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

  function send(sessionId: number, content: string, msgType = 'TEXT'): string | null {
    if (!socket || socket.readyState !== WebSocket.OPEN) return null;
    const clientMsgId = `ws_${newIdempotencyKey('chat')}`;
    localSeq += 1;
    const envelope: WsEnvelope = {
      type: 'MSG',
      sessionId,
      seq: localSeq,
      payload: JSON.stringify({ clientMsgId, content, senderType: 'CUSTOMER', msgType }),
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
      payload: JSON.stringify({ typing: true, senderType: 'CUSTOMER' }),
      timestamp: Date.now(),
    };
    socket.send(JSON.stringify(envelope));
  }

  onUnmounted(close);
  return { connected, connect, close, send, sendTyping };
}
