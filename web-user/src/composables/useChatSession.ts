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

/** 业务错误码：会话状态冲突（已结束 / 已关闭），后端 ErrorCode.STATE_CONFLICT。 */
const STATE_CONFLICT = 40901;

/** 业务错误码：消息命中敏感词（后端 ErrorCode.SENSITIVE_MESSAGE），直接阻断、不打码。 */
const SENSITIVE_MESSAGE = 40003;

/** 页面侧回调：实时通道的拒绝结果拿不到 Promise，只能靠 ACK 回调回来。 */
export interface ChatSessionHandlers {
  /**
   * 服务端拒绝发送（如命中敏感词）：把原文还给输入框并提示，让用户自己改。
   * <p>不在这里 ElMessage，是因为 composable 不引 UI 库；页面才知道提示应该弹在哪。</p>
   */
  onSendRejected?: (reason: string, content: string) => void;
}

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

export function useChatSession(customerId: number, vip = false, handlers: ChatSessionHandlers = {}) {
  const messages = ref<ChatMessage[]>([]);
  const sessionId = ref<number | null>(null);
  const online = ref(false);
  const realtime = ref(false);
  /** 会话状态：IDLE 尚未咨询 / WAITING 排队中 / ACTIVE 已接入 / CLOSED 已结束 */
  const status = ref<string>('IDLE');
  /** 会话是否已结束：客户需看到「本次会话已结束」，并可主动重新咨询 */
  const closed = ref(false);
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
    onClosed: (closedSessionId: number, reason: string) => onClosed(closedSessionId, reason),
  });

  /** 服务端已结束会话：客户侧立刻冻结输入并显示结束提示；重新咨询由客户主动触发。 */
  function onClosed(closedSessionId: number, reason: string): void {
    if (sessionId.value !== null && Number(closedSessionId) !== sessionId.value) return;
    if (closed.value) return;
    status.value = 'CLOSED';
    closed.value = true;
    queueAhead.value = 0;
    stopQueuePoll();
    seq += 1;
    messages.value.push({
      seq: -seq, senderType: 'SYSTEM', msgType: 'TEXT',
      content: reason === 'TIMEOUT' ? '本次会话已结束（长时间未发言）' : '本次会话已结束',
      createdAtMillis: Date.now(), self: false,
    });
  }

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
      // 用信封时间戳（服务端落库时刻），不用本地 Date.now()：离线补发的消息
      // 若按收包时间算，会让「5 分钟重新显示时间」的分隔错位
      createdAtMillis: Number(m.createdAt) || Date.now(),
      self: false,
    });
  }

  function onAck(ack: IncomingAck): void {
    const key = ack.clientMsgId;
    if (!key || !pending.has(key)) return;
    const queued = pending.get(key) as { content: string; msgType: string };
    pending.delete(key);
    if (ack.code) {
      // 服务端拒绝（会话关闭/参数错误/敏感词等）：先撤回乐观消息，别让脏气泡留在时间线里
      messages.value = messages.value.filter((m) => m.clientMsgId !== key);
      if (ack.code === SENSITIVE_MESSAGE) {
        // 敏感词：不打码、不重试（REST 兜底也是同一个 40003），把原文还回输入框让用户自己改
        handlers.onSendRejected?.(
          ack.reason || '发送失败：消息包含违禁词，请文明发言',
          queued.content,
        );
        return;
      }
      // 其余错误改走 REST 发一次（REST 会给出明确错误）
      // REST 侧会自行处理「会话已结束 → 重开会话补发」，这里只需吃掉未处理的 reject
      void sendViaRest(queued.content, key, queued.msgType).catch(() => undefined);
      return;
    }
    // ACCEPTED / DUPLICATE：以服务端历史为准，消除乐观序号与真实 seq 的偏差
    void refresh();
  }

  /**
   * 会话已被服务端结束（40901 STATE_CONFLICT）时重开一条。
   * <p>典型场景：客户开着客服页离开 10 分钟，服务端自动结束会话（timeoutClose），
   * 回来再发消息就撞上「已结束」。此处重建会话后把这句话补发出去，
   * 客户看到的只是「已为你重新接入」，而不是一条发不出去的失败提示。</p>
   */
  async function reviveClosedSession(): Promise<boolean> {
    try {
      await ensureSession();
      closed.value = false;
      seq += 1;
      messages.value.push({
        seq: -seq, senderType: 'SYSTEM', msgType: 'TEXT',
        content: '上次会话已结束，已为你重新接入客服', createdAtMillis: Date.now(), self: false,
      });
      return true;
    } catch {
      return false;
    }
  }

  /** 客户主动点「重新咨询」：显式建新会话，复用 40901 自动补发那条链路。 */
  async function reopen(): Promise<void> {
    if (!closed.value && sessionId.value !== null) return;
    messages.value = messages.value.filter((m) => m.msgType !== 'SATISFY');
    await reviveClosedSession();
    await refresh();
  }

  async function ensureSession(): Promise<void> {
    const res = await chatApi.createSession(customerId, vip);
    // Long → String：统一转 number，后续与 WS 帧里的数字 sessionId 才能严格相等
    sessionId.value = Number(res.data.sessionId);
    closed.value = false;
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

  async function sendViaRest(
    content: string,
    clientMsgId: string,
    msgType = 'TEXT',
    allowRevive = true,
  ): Promise<void> {
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
      const code = (e as { code?: number })?.code;
      if (allowRevive && code === STATE_CONFLICT) {
        // 会话已结束（10 分钟无消息自动结束 / 客服结束）：重开会话后补发一次，只重试一次防死循环
        messages.value = messages.value.filter((m) => m.clientMsgId !== clientMsgId);
        if (await reviveClosedSession()) {
          await sendViaRest(content, clientMsgId, msgType, false);
          return;
        }
      }
      tmp.pending = false;
      // 敏感词被拒：这条消息根本没落库，留在时间线里会让人以为「发出去了只是没回」
      if (code === SENSITIVE_MESSAGE) {
        messages.value = messages.value.filter((m) => m.clientMsgId !== clientMsgId);
      }
      throw e;
    }
  }

  /**
   * 发送消息。
   * @param msgType TEXT（默认）/ IMAGE —— IMAGE 时 content 必须是 fileApi.upload 返回的 fileId
   */
  async function send(content: string, msgType = 'TEXT'): Promise<void> {
    // 进入聊天页只查不建：第一句话离开页面前不存在会话，这里才真正建立咨询。
    if (closed.value) await reopen();
    if (sessionId.value === null) await ensureSession();
    const sid = sessionId.value;
    if (sid === null) return;
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
    // 只查不建：仅当客户真的发出首条消息时 send() 才调 createSession。
    chatApi.currentOpen(customerId)
      .then((res) => {
        const vo = res.data;
        if (vo) {
          sessionId.value = Number(vo.sessionId);
          status.value = String(vo.status ?? 'WAITING');
          online.value = true;
          assignedNotified = status.value !== 'WAITING';
          if (status.value === 'WAITING') startQueuePoll();
          else stopQueuePoll();
        } else {
          sessionId.value = null;
          status.value = 'IDLE';
          online.value = true;
        }
        return refresh();
      })
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
    messages, sessionId, online, realtime, status, closed, queueAhead, queued, peerTyping,
    start, stop, send, refresh, notifyTyping, reopen,
  };
}
