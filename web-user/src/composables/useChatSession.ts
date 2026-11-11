/**
 * 用户端客服会话封装（P6-55，收口阶段切回实时通道）。
 *
 * 后端 ChatWebSocketHandler 收口后已接受 aud=user、按连接身份落 CUSTOMER，并把客服回复广播到客户连接，
 * 因此实时收发走 WebSocket；REST 仅用于「建会话 / 拉历史」以及 WS 未连接时的发送与同步兜底。
 * 页面仍只依赖 { messages, sessionId, online, start, stop, send, refresh }，切换对视图无感。
 */
import { onUnmounted, ref } from 'vue';
import { chatApi, type MessageVO } from '@/api/chat';
import { newIdempotencyKey } from '@/utils/idempotent';
import { useChatSocket, type IncomingAck, type IncomingMessage } from '@/composables/useChatSocket';

export interface ChatMessage {
  seq: number;
  senderType: string;
  content: string;
  clientMsgId?: string;
  createdAtMillis: number;
  self: boolean;
  pending?: boolean;
}

function toMessage(m: MessageVO): ChatMessage {
  return {
    seq: m.seq,
    senderType: m.senderType,
    content: m.content,
    clientMsgId: m.clientMsgId ?? undefined,
    createdAtMillis: m.createdAtMillis,
    self: m.senderType === 'CUSTOMER',
  };
}

export function useChatSession(customerId: number, vip = false) {
  const messages = ref<ChatMessage[]>([]);
  const sessionId = ref<number | null>(null);
  const online = ref(false);
  const realtime = ref(false);
  let timer: ReturnType<typeof setInterval> | undefined;
  let seq = 0;
  /** clientMsgId -> 内容，用于 ACK 校准与 WS 失败时的 REST 补发 */
  const pending = new Map<string, string>();

  const socket = useChatSocket({
    onMessage: (m: IncomingMessage) => onIncoming(m),
    onAck: (ack: IncomingAck) => onAck(ack),
    onConnected: () => { realtime.value = true; },
    onDisconnected: () => { realtime.value = false; },
  });

  function onIncoming(m: IncomingMessage): void {
    // 后端 Jackson 把 Long 序列化成字符串（防 JS 精度丢失），WS 帧里是数字、REST 里是 "34"，
    // 这里必须统一成 number 再比，否则会静默丢弃所有实时推送（页面上表现为「客服没回」）。
    if (sessionId.value === null || Number(m.sessionId) !== sessionId.value) return;
    if (m.clientMsgId && messages.value.some((x) => x.clientMsgId === m.clientMsgId)) return;
    messages.value.push({
      seq: m.seq,
      senderType: m.senderType || 'STAFF',
      content: m.content,
      clientMsgId: m.clientMsgId || undefined,
      createdAtMillis: Date.now(),
      self: false,
    });
  }

  function onAck(ack: IncomingAck): void {
    const key = ack.clientMsgId;
    if (!key || !pending.has(key)) return;
    const content = pending.get(key) as string;
    pending.delete(key);
    if (ack.code) {
      // 服务端拒绝（会话关闭/参数错误等）：撤回乐观消息，改走 REST 发一次（REST 会给出明确错误）
      messages.value = messages.value.filter((m) => m.clientMsgId !== key);
      void sendViaRest(content, key);
      return;
    }
    // ACCEPTED / DUPLICATE：以服务端历史为准，消除乐观序号与真实 seq 的偏差
    void refresh();
  }

  async function ensureSession(): Promise<void> {
    const res = await chatApi.createSession(customerId, vip);
    // Long → String：统一转 number，后续与 WS 帧里的数字 sessionId 才能严格相等
    sessionId.value = Number(res.data.sessionId);
    online.value = true;
  }

  async function refresh(): Promise<void> {
    if (sessionId.value === null) return;
    try {
      const list = await chatApi.history(sessionId.value, 0);
      const latest = (list.data ?? []).map(toMessage);
      // 保留仍在途的乐观消息，避免刷新时闪烁
      const optimistic = messages.value.filter((m) => m.pending && pending.has(m.clientMsgId ?? ''));
      messages.value = [...latest, ...optimistic];
    } catch {
      /* 历史同步失败静默，不断开 */
    }
  }

  async function sendViaRest(content: string, clientMsgId: string): Promise<void> {
    if (sessionId.value === null) return;
    seq += 1;
    const tmp: ChatMessage = {
      seq: -seq, senderType: 'CUSTOMER', content, clientMsgId,
      createdAtMillis: Date.now(), self: true, pending: true,
    };
    messages.value.push(tmp);
    try {
      await chatApi.sendMessage(sessionId.value, { senderType: 'CUSTOMER', content, clientMsgId });
      await refresh();
      messages.value = messages.value.filter((m) => !(m.pending && m.clientMsgId === clientMsgId));
    } catch (e) {
      tmp.pending = false;
      throw e;
    }
  }

  async function send(content: string): Promise<void> {
    if (sessionId.value === null) return;
    const sid = sessionId.value;
    const wsKey = socket.connected.value ? socket.send(sid, content) : null;
    if (wsKey) {
      pending.set(wsKey, content);
      seq += 1;
      messages.value.push({
        seq: -seq, senderType: 'CUSTOMER', content, clientMsgId: wsKey,
        createdAtMillis: Date.now(), self: true, pending: true,
      });
      return; // ACK 异步校准
    }
    await sendViaRest(content, `u_${newIdempotencyKey('chat')}`);
  }

  function start(): void {
    ensureSession()
      .then(refresh)
      .then(() => {
        socket.connect();
        // WS 未连上时才用轮询兜底；连上后轮询空转，不产生请求
        timer = setInterval(() => {
          if (!socket.connected.value) void refresh();
        }, 3000);
      })
      .catch(() => { online.value = false; });
  }

  function stop(): void {
    if (timer) clearInterval(timer);
    timer = undefined;
    socket.close();
    realtime.value = false;
  }

  onUnmounted(stop);

  return { messages, sessionId, online, realtime, start, stop, send, refresh };
}
