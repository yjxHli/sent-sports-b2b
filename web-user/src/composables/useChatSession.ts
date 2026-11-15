/**
 * 用户端客服会话封装（P6-55，收口阶段切回实时通道）。
 *
 * 后端 ChatWebSocketHandler 收口后已接受 aud=user、按连接身份落 CUSTOMER，并把客服回复广播到客户连接，
 * 因此实时收发走 WebSocket；REST 仅用于「建会话 / 拉历史」以及 WS 未连接时的发送与同步兜底。
 * 页面仍只依赖 { messages, sessionId, online, start, stop, send, refresh }，切换对视图无感。
 */
import { computed, onUnmounted, ref } from 'vue';
import { chatApi, type MessageVO } from '@/api/chat';
import { newIdempotencyKey } from '@/utils/idempotent';
import { useChatSocket, type IncomingAck, type IncomingMessage } from '@/composables/useChatSocket';

export interface ChatMessage {
  seq: number;
  senderType: string;
  /** TEXT / IMAGE；IMAGE 时 content 是 fileId */
  msgType: string;
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
    msgType: m.msgType ?? 'TEXT',
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
  /** 会话状态：WAITING 排队中 / ACTIVE 已接入 / CLOSED 已结束 */
  const status = ref<string>('WAITING');
  /** 前方排队人数（position 从 1 开始，展示给用户时要减 1） */
  const queueAhead = ref(0);
  /** 客服正在输入（收到 TYPING 帧后亮 3s，期间没有新帧就自动熄灭） */
  const peerTyping = ref(false);
  const queued = computed(() => status.value === 'WAITING');
  let timer: ReturnType<typeof setInterval> | undefined;
  let queueTimer: ReturnType<typeof setInterval> | undefined;
  let typingOffTimer: ReturnType<typeof setTimeout> | undefined;
  let lastTypingSentAt = 0;
  let seq = 0;
  /** 「客服已接入」系统提示只补一次：排队轮询与 ASSIGNED 推送都可能先到 */
  let assignedNotified = false;
  /** clientMsgId -> 待确认消息（内容 + 类型），用于 ACK 校准与 WS 失败时的 REST 补发 */
  const pending = new Map<string, { content: string; msgType: string }>();

  const socket = useChatSocket({
    onMessage: (m: IncomingMessage) => onIncoming(m),
    onAck: (ack: IncomingAck) => onAck(ack),
    onConnected: () => { realtime.value = true; },
    onDisconnected: () => { realtime.value = false; },
    onAssigned: (assignedSessionId: number) => onAssigned(assignedSessionId),
    onTyping: (typingSessionId: number) => onPeerTyping(typingSessionId),
  });

  /** 收到「客服正在输入」：亮提示并重置 3s 熄灭定时器 */
  function onPeerTyping(typingSessionId: number): void {
    if (sessionId.value !== null && Number(typingSessionId) !== sessionId.value) return;
    peerTyping.value = true;
    if (typingOffTimer) clearTimeout(typingOffTimer);
    typingOffTimer = setTimeout(() => { peerTyping.value = false; }, 3000);
  }

  /**
   * 上报「我正在输入」：1s 节流，避免每个按键都发一帧（太长的窗口会吞掉「停一下再打」的提示）。
   * 弱语义、不重试——WS 没连上就静默丢弃，不影响正常收发消息。
   */
  function notifyTyping(): void {
    if (sessionId.value === null || status.value === 'WAITING') return;
    const now = Date.now();
    if (now - lastTypingSentAt < 1000) return;
    lastTypingSentAt = now;
    socket.sendTyping(sessionId.value);
  }

  function onIncoming(m: IncomingMessage): void {
    // 后端 Jackson 把 Long 序列化成字符串（防 JS 精度丢失），WS 帧里是数字、REST 里是 "34"，
    // 这里必须统一成 number 再比，否则会静默丢弃所有实时推送（页面上表现为「客服没回」）。
    if (sessionId.value === null || Number(m.sessionId) !== sessionId.value) return;
    if (m.clientMsgId && messages.value.some((x) => x.clientMsgId === m.clientMsgId)) return;
    messages.value.push({
      seq: m.seq,
      senderType: m.senderType || 'STAFF',
      msgType: m.msgType || 'TEXT',
      content: m.content,
      clientMsgId: m.clientMsgId || undefined,
      createdAtMillis: Date.now(),
      self: false,
    });
  }

  function onAck(ack: IncomingAck): void {
    const key = ack.clientMsgId;
    if (!key || !pending.has(key)) return;
    const queued = pending.get(key) as { content: string; msgType: string };
    pending.delete(key);
    if (ack.code) {
      // 服务端拒绝（会话关闭/参数错误等）：撤回乐观消息，改走 REST 发一次（REST 会给出明确错误）
      messages.value = messages.value.filter((m) => m.clientMsgId !== key);
      void sendViaRest(queued.content, key, queued.msgType);
      return;
    }
    // ACCEPTED / DUPLICATE：以服务端历史为准，消除乐观序号与真实 seq 的偏差
    void refresh();
  }

  async function ensureSession(): Promise<void> {
    const res = await chatApi.createSession(customerId, vip);
    // Long → String：统一转 number，后续与 WS 帧里的数字 sessionId 才能严格相等
    sessionId.value = Number(res.data.sessionId);
    status.value = String(res.data.status ?? 'WAITING');
    assignedNotified = status.value !== 'WAITING';
    online.value = true;
    if (status.value === 'WAITING') {
      startQueuePoll();
    } else {
      stopQueuePoll();
    }
  }

  /** 排队轮询：仅 WAITING 时每 5s 拉一次位置，接入后自动停 */
  function startQueuePoll(): void {
    if (queueTimer) return;
    queueTimer = setInterval(() => {
      if (status.value !== 'WAITING' || sessionId.value === null) {
        stopQueuePoll();
        return;
      }
      void refreshQueue();
    }, 5000);
  }

  function stopQueuePoll(): void {
    if (queueTimer) clearInterval(queueTimer);
    queueTimer = undefined;
  }

  async function refreshQueue(): Promise<void> {
    if (sessionId.value === null) return;
    try {
      const res = await chatApi.queue(sessionId.value);
      const queue = res.data;
      if (!queue) return;
      status.value = String(queue.status);
      queueAhead.value = queue.position > 0 ? Number(queue.position) - 1 : 0;
      if (status.value !== 'WAITING') {
        stopQueuePoll();
      }
    } catch {
      /* 位置同步失败静默，不打断聊天 */
    }
  }

  /** 服务端推送「已接入客服」：隐藏排队提示、解锁输入、补一条系统提示（只补一次） */
  function onAssigned(assignedSessionId: number): void {
    if (sessionId.value !== null && assignedSessionId && assignedSessionId !== sessionId.value) return;
    status.value = 'ACTIVE';
    queueAhead.value = 0;
    stopQueuePoll();
    if (assignedNotified) return;
    assignedNotified = true;
    seq += 1;
    messages.value.push({
      seq: -seq,
      senderType: 'SYSTEM',
      msgType: 'TEXT',
      content: '客服已接入，请开始沟通',
      createdAtMillis: Date.now(),
      self: false,
    });
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

  async function sendViaRest(content: string, clientMsgId: string, msgType = 'TEXT'): Promise<void> {
    if (sessionId.value === null) return;
    seq += 1;
    const tmp: ChatMessage = {
      seq: -seq, senderType: 'CUSTOMER', msgType, content, clientMsgId,
      createdAtMillis: Date.now(), self: true, pending: true,
    };
    messages.value.push(tmp);
    try {
      await chatApi.sendMessage(sessionId.value, { senderType: 'CUSTOMER', content, clientMsgId, msgType });
      await refresh();
      messages.value = messages.value.filter((m) => !(m.pending && m.clientMsgId === clientMsgId));
    } catch (e) {
      tmp.pending = false;
      throw e;
    }
  }

  /**
   * 发送消息。
   * @param msgType TEXT（默认）/ IMAGE —— IMAGE 时 content 必须是 fileApi.upload 返回的 fileId
   */
  async function send(content: string, msgType = 'TEXT'): Promise<void> {
    if (sessionId.value === null) return;
    const sid = sessionId.value;
    const wsKey = socket.connected.value ? socket.send(sid, content, msgType) : null;
    if (wsKey) {
      pending.set(wsKey, { content, msgType });
      seq += 1;
      messages.value.push({
        seq: -seq, senderType: 'CUSTOMER', msgType, content, clientMsgId: wsKey,
        createdAtMillis: Date.now(), self: true, pending: true,
      });
      return; // ACK 异步校准
    }
    await sendViaRest(content, `u_${newIdempotencyKey('chat')}`, msgType);
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
    if (typingOffTimer) clearTimeout(typingOffTimer);
    typingOffTimer = undefined;
    stopQueuePoll();
    socket.close();
    realtime.value = false;
  }

  onUnmounted(stop);

  return {
    messages, sessionId, online, realtime, status, queueAhead, queued, peerTyping,
    start, stop, send, refresh, notifyTyping,
  };
}
