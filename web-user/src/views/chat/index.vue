<template>
  <div class="chat-page">
    <div class="chat-shell">
      <div class="chat-card">
        <!-- 头部：返回 + 客服名 + 连接状态（对齐参考图的浮层标题栏） -->
        <header class="chat-head">
          <button class="chat-back" title="返回" @click="goBack">←</button>
          <div class="chat-head__title">
            <b>森特客服</b>
            <span class="chat-state" :class="{ 'chat-state--on': online }">
              {{ online ? '在线 · 秒回' : '连接中…' }}
            </span>
          </div>
          <div class="chat-head__actions">
            <button
              type="button"
              class="chat-head__link"
              data-testid="chat-service-progress"
              @click="openProgress"
            >
              服务进度
            </button>
          </div>
        </header>

        <!-- 历史咨询入口：常驻一条细栏，别做成按钮挤在标题栏里（参考图就是这位置） -->
        <div class="chat-history-bar">
          <button
            type="button"
            class="chat-history-bar__link"
            data-testid="chat-history-link"
            @click="openHistory"
          >
            点击查看更多历史咨询记录 ›
          </button>
        </div>

        <!-- 消息区 -->
        <div ref="listRef" class="chat-body">
          <div class="chat-tip">想咨询订单、定制、发票或退换货，直接说就好～</div>

          <!-- 机器人迎宾：换成与真实回复同款的气泡（头像 + 名字），还没开口时才出现 -->
          <div v-if="showBotWelcome" class="chat-welcome" data-testid="bot-welcome">
            <div class="chat-msg chat-msg--other">
              <div class="avatar avatar--bot" aria-hidden="true">🤖</div>
              <div class="chat-bubble">
                <b class="chat-bubble__name">智能助手小森</b>
                您好，我是智能助手小森，请问有什么可以帮您？
              </div>
            </div>
          </div>

          <div v-for="(m, i) in messages" :key="m.seq + (m.clientMsgId || '')">
            <!-- 微信式时间提示：相邻消息间隔满 5 分钟就重新显示当前时间（首条必显） -->
            <div
              v-if="shouldShowTimeDivider(messages[i - 1]?.createdAtMillis, m.createdAtMillis)"
              class="chat-time"
              data-testid="chat-time-divider"
            >{{ fmtChatTime(m.createdAtMillis) }}</div>
            <!-- 服务评价卡必须排在 SYSTEM 文本分支之前：卡片 senderType 也是 SYSTEM -->
            <div v-if="m.msgType === 'SATISFY'" class="chat-satisfy" data-testid="satisfaction-card">
              <template v-if="satisfactionOf(m).status !== 'RATED'">
                <b>⭐ 请评价本次服务</b>
                <span class="chat-satisfy__hint">您的评价会直接反馈给刚刚接待您的客服</span>
                <div class="chat-satisfy__btns">
                  <button
                    v-for="opt in SATISFY_OPTIONS"
                    :key="opt.value"
                    :data-testid="`satisfaction-${opt.value.toLowerCase()}`"
                    :disabled="rating"
                    @click="rateSatisfaction(opt.value, m)"
                  >{{ opt.label }}</button>
                </div>
              </template>
              <template v-else>
                <b>已评价：{{ scoreLabel(satisfactionOf(m).score) }}</b>
                <span class="chat-satisfy__hint">感谢您的反馈</span>
              </template>
            </div>
            <div v-else-if="m.senderType === 'SYSTEM'" class="chat-system">{{ m.content }}</div>
            <div
              v-else
              class="chat-msg"
              :class="m.self ? 'chat-msg--self' : 'chat-msg--other'"
              :data-testid="m.senderType === 'BOT' ? 'chat-bot-msg' : null"
            >
              <!-- 机器人说的话按正常会话渲染：机器人头像 + 气泡，不再走居中灰字旁白 -->
              <div
                v-if="!m.self"
                class="avatar"
                :class="m.senderType === 'BOT' ? 'avatar--bot' : 'avatar--staff'"
              >{{ m.senderType === 'BOT' ? '🤖' : '客服' }}</div>
              <!-- 订单卡片：选中订单后发给客服，双方都看到同一张卡 -->
              <ChatOrderCard
                v-if="m.msgType === 'ORDER'"
                :content="m.content"
                :self="m.self"
              />
              <!-- 图片消息：content 是 fileId，直接拼文件服务地址；点开看大图 -->
              <div
                v-else-if="m.msgType === 'IMAGE'"
                class="chat-bubble chat-bubble--img"
                :class="{ 'chat-bubble--pending': m.pending }"
              >
                <img
                  class="chat-img"
                  :src="fileApi.url(m.content)"
                  alt="图片消息"
                  @click="previewImage(m.content)"
                />
              </div>
              <div v-else class="chat-bubble" :class="{ 'chat-bubble--pending': m.pending }">
                {{ m.content }}
              </div>
              <div v-if="m.self" class="avatar avatar--me">{{ meBadge }}</div>
            </div>
          </div>

          <!-- 客服正在输入：收到 TYPING 帧时亮起，3s 无新帧自动熄灭 -->
          <div v-if="peerTyping" class="chat-msg chat-msg--other typing-row">
            <div class="avatar avatar--staff">客服</div>
            <div class="chat-bubble chat-bubble--typing">
              <span class="dot" /><span class="dot" /><span class="dot" />
              <em>客服正在回复中…</em>
            </div>
          </div>

          <!-- 快捷问题：首屏没有消息时展示，点一下直接发出去（双列芯片，不用整行列表） -->
          <div v-if="messages.length === 0" class="quick-grid">
            <button
              v-for="q in QUICK_QUESTIONS"
              :key="q"
              class="quick-chip"
              @click="askQuick(q)"
            >{{ q }}</button>
          </div>
        </div>

        <!-- 排队提示：WAITING 时常驻，收到「已接入客服」后自动消失 -->
        <div v-if="queued" class="queue-banner">
          <span class="queue-dot" />
          当前咨询人数较多，正在排队中，前方还有 {{ queueAhead }} 人
        </div>

        <!-- 会话结束：客服主动关闭或 10 分钟超时后推 CLOSED 帧，不自动消失，客户可主动重新咨询 -->
        <div v-if="closed" class="closed-banner" data-testid="chat-closed-banner">
          <span>本次会话已结束</span>
          <button type="button" data-testid="chat-reopen" @click="reopen">重新咨询</button>
        </div>

        <!-- 输入区：一行排下来 ——「发订单给客服」+ 四个工具 + 输入框 + 发送 -->
        <footer class="chat-input">
          <div class="chat-input__row">
            <!-- 「发订单给客服」矩形：宽度与右侧四个工具按钮总宽对齐（4×30 + 3×6 = 138px） -->
            <button
              type="button"
              class="send-order-banner"
              data-testid="chat-send-order-banner"
              title="发订单给客服，问题定位更快"
              @click="openOrderDialog"
            >发订单给客服</button>

            <div class="chat-input__tools">
              <button
                class="tool"
                title="发送订单"
                data-testid="chat-tool-order"
                @click="openOrderDialog"
              >📦</button>
              <button
                class="tool"
                title="上传图片"
                data-testid="chat-tool-image"
                :disabled="queued || uploadingImg"
                @click="pickImage"
              >
                {{ uploadingImg ? '…' : '🖼' }}
              </button>
              <!-- 拍照按需求暂不实现：给明确提示，别做成点了没反应的死按钮 -->
              <button
                class="tool"
                title="拍照"
                data-testid="chat-tool-camera"
                @click="onCamera"
              >📷</button>
              <button
                class="tool"
                :class="{ 'tool--on': emojiOpen }"
                title="表情"
                data-testid="chat-tool-emoji"
                @click="toggleEmoji"
              >😊</button>
              <!-- 拍照 / 相册：accept=image/* 在手机浏览器会同时给「拍照」和「图库」两个入口 -->
              <input
                ref="imageInput"
                class="hidden-input"
                type="file"
                accept="image/*"
                @change="onImagePicked"
              />
            </div>

            <div class="chat-input__field">
              <input
                v-model="draft"
                :disabled="queued || closed"
                :placeholder="closed ? '本次会话已结束，请点击重新咨询' : (queued ? '排队中，接入后可发送消息' : '请输入想咨询的问题')"
                @input="onDraftInput"
                @keyup.enter="doSend"
              />
              <button class="send-btn" :disabled="queued || closed || sending" @click="doSend">
                {{ sending ? '发送中' : '发送' }}
              </button>
            </div>
          </div>

          <!-- 表情面板：点一个塞进输入框，不直接发（客户常要图文混着说） -->
          <div v-if="emojiOpen" class="chat-emoji" data-testid="chat-emoji-panel">
            <button
              v-for="e in EMOJIS"
              :key="e"
              type="button"
              class="chat-emoji__item"
              @click="insertEmoji(e)"
            >{{ e }}</button>
          </div>

          <!-- 机器人接待中：给一个直达人工的快捷入口，别逼客户自己想办法 -->
          <div v-if="botMode && !closed" class="chat-handoff">
            <button
              type="button"
              class="chat-handoff__btn"
              data-testid="chat-transfer-human"
              @click="transferToHuman"
            >转人工</button>
            <span class="chat-handoff__hint">小森答不好？点这里直接找人工客服</span>
          </div>
        </footer>
      </div>

      <!-- 右侧不再有订单侧栏（2026-11-24 用户要求）：订单入口统一收敛到「发订单给客服」弹窗 -->
    </div>

    <!-- 点图片看大图（Element Plus 全局注册，无需再 import） -->
    <el-image-viewer
      v-if="previewSrc"
      :url-list="[previewSrc]"
      @close="previewSrc = ''"
    />

    <!-- 订单 / 非订单问题 选择弹窗（输入区 📦 与「发订单给客服」横幅共用） -->
    <ChatOrderPickerDialog
      :open="orderDialogOpen"
      :orders="myOrders"
      :loading="ordersLoading"
      :sending-order="sendingOrder"
      :disabled="queued || closed"
      @close="orderDialogOpen = false"
      @send="sendOrderCardFromDialog"
      @faq="onFaqPick"
    />

    <!-- 历史咨询记录 -->
    <ChatHistoryDialog
      :open="historyOpen"
      :sessions="sessions"
      :loading="sessionsLoading"
      @close="historyOpen = false"
      @load="loadSessions"
    />

    <!-- 服务进度 / 历史服务进度 -->
    <ChatServiceProgressDialog
      :open="progressOpen"
      :sessions="sessions"
      :loading="sessionsLoading"
      :current="currentProgress"
      :queued="queued"
      :queue-ahead="queueAhead"
      @close="progressOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 用户端在线客服（对齐「淘小蜜」式浮层：顶部标题栏 + 渐变消息区 + 快捷问题 + 底部输入条）。
 *
 * 实时能力仍走 useChatSession：
 * - WAITING → 顶部排队条 + 输入禁用；收到 ASSIGNED 自动解锁；
 * - 对方正在输入：收到 TYPING 帧亮「客服正在回复中…」，3s 无新帧熄灭；
 * - 我方正在输入：输入框每次按键（节流）向客服端发一帧 TYPING。
 *
 * 订单协同（2026-11-15，2026-11-24 改版）：
 * - 入口只剩两个：输入区左侧「发订单给客服」矩形与 📦 按钮，都打开同一个双 Tab 弹窗；
 *   原「查看订单」按钮 + 右侧订单侧栏已按用户要求删除（弹窗已覆盖同一功能）；
 * - 选中一单点「发给客服」，把订单摘要以 msgType=ORDER 发出，双方渲染成同一张订单卡片；
 * - 客服端另有右侧「顾客历史订单」面板，可看到该顾客最近买过什么。
 *
 * 消息渲染口径（2026-11-24 用户要求）：
 * - BOT（智能助手小森）说的话按正常会话气泡渲染，带头像，不再做成居中灰字旁白；
 * - SYSTEM（已接入 / 已结束）才是居中浅灰提示。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  SATISFY_LABELS, fmtChatTime, shouldShowTimeDivider,
} from '@sent/shared';
import { useChatSession } from '@/composables/useChatSession';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { fileApi } from '@/api/file';
import { orderApi, type OrderListItem } from '@/api/order';
import { chatApi, type SessionSummaryVO } from '@/api/chat';
import { HttpError, unwrap } from '@/utils/http';
import ChatOrderCard from '@/components/ChatOrderCard.vue';
import ChatOrderPickerDialog from '@/components/ChatOrderPickerDialog.vue';
import ChatHistoryDialog from '@/components/ChatHistoryDialog.vue';
import ChatServiceProgressDialog from '@/components/ChatServiceProgressDialog.vue';

const router = useRouter();
const auth = useAuthStore();
const draft = ref('');
const sending = ref(false);
const listRef = ref<HTMLElement | null>(null);
const imageInput = ref<HTMLInputElement | null>(null);
const uploadingImg = ref(false);

const customerId = auth.userId;
const chat = customerId
  ? useChatSession(customerId, false, {
      // 敏感词被拒（WS 通道）：撤回气泡 + 原文还回输入框，让用户自己改，绝不替他打码
      onSendRejected: (reason: string, content: string) => {
        draft.value = content;
        ElMessage.error(reason);
      },
    })
  : null;
const messages = chat ? chat.messages : ref([]);
const online = chat ? chat.online : ref(false);
const queued = chat ? chat.queued : ref(false);
const botMode = chat ? chat.botMode : ref(false);
const queueAhead = chat ? chat.queueAhead : ref(0);
const peerTyping = chat ? chat.peerTyping : ref(false);
const sessionId = chat ? chat.sessionId : ref<number | null>(null);
const closed = chat ? chat.closed : ref(false);

/** 服务评价（2026-11-17）：三档打分，重复提交由后端唯一索引幂等兜底 */
const rating = ref(false);
const SATISFY_OPTIONS = [
  { value: 'SATISFIED', label: '满意' },
  { value: 'NORMAL', label: '一般' },
  { value: 'UNSATISFIED', label: '不满意' },
];

function satisfactionOf(m: { content: string }): { status: string; score: string } {
  try {
    const parsed = JSON.parse(m.content || '{}');
    return { status: String(parsed.status ?? 'PENDING'), score: String(parsed.score ?? '') };
  } catch {
    return { status: 'PENDING', score: '' };
  }
}

function scoreLabel(score: string): string {
  return SATISFY_LABELS[score] ?? score;
}

async function rateSatisfaction(score: string, m: { content: string }): Promise<void> {
  const sid = Number(sessionId.value ?? 0);
  if (!sid || rating.value) return;
  rating.value = true;
  try {
    await chatApi.rateSatisfaction(sid, score);
    // 原卡片就地变「已评价」，不新增消息；客服端由 WS 更新帧同步
    m.content = JSON.stringify({ status: 'RATED', score });
    ElMessage.success('感谢您的评价');
  } catch (e) {
    ElMessage.error((e as Error)?.message || '评价提交失败，请重试');
  } finally {
    rating.value = false;
  }
}

const QUICK_QUESTIONS = [
  '这件商品有现货吗？多久能发货？',
  '定制球衣印队徽，多少件起订？',
  '可以开增值税专用发票吗？',
  '收到的商品不合适，怎么退换？',
];

/** 机器人迎宾只在「还没有任何真实对话」时出现：客户一开口就收起，不占聊天区地方 */
const showBotWelcome = computed(() => botMode.value
  && !messages.value.some((m) => m.senderType !== 'SYSTEM'));

/** 快捷转人工：就是替客户发一句「转人工」，走同一条机器人指令链路 */
async function transferToHuman(): Promise<void> {
  if (!chat || closed.value) return;
  sending.value = true;
  try {
    await chat.send('转人工');
    scrollBottom();
  } catch (e) {
    ElMessage.error((e as Error).message || '转接失败，请稍后再试');
  } finally {
    sending.value = false;
  }
}

/** 自己气泡上的头像文字：商城端不缓存昵称，统一用「我」 */
const meBadge = '我';

/* ---------------- 订单弹窗（唯一入口：发订单给客服 / 📦） ---------------- */
const ordersLoading = ref(false);
const myOrders = ref<OrderListItem[]>([]);
const sendingOrder = ref('');

function scrollBottom(): void {
  nextTick(() => {
    const el = listRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

function onDraftInput(): void {
  // 节流在 composable 内做，这里只管触发
  chat?.notifyTyping();
}

function insertText(text: string): void {
  draft.value = text;
  onDraftInput();
}

/** 客户主动重新咨询：重新建会话后再允许发送 */
async function reopen(): Promise<void> {
  if (!chat) return;
  try {
    await chat.reopen();
    ElMessage.success('已重新接入客服');
  } catch (e) {
    ElMessage.error((e as Error).message || '重新接入失败，请稍后再试');
  }
}

async function doSend(): Promise<void> {
  const text = draft.value.trim();
  if (!text || !chat || closed.value) return;
  sending.value = true;
  try {
    await chat.send(text);
    // 只有成功才清空：被拒绝（如命中敏感词）时必须留着原文让用户自己改
    draft.value = '';
    scrollBottom();
  } catch (e) {
    // REST 兜底通道：40003 固定给文明发言提示，其余直接展示后端文案
    ElMessage.error(e instanceof HttpError && e.code === 40003
      ? '输入包含违禁词，请文明发言'
      : (e as Error).message || '发送失败');
  } finally {
    sending.value = false;
  }
}

async function askQuick(text: string): Promise<void> {
  draft.value = text;
  await doSend();
}

/** 图片预览地址（空串 = 关闭） */
const previewSrc = ref('');

function previewImage(fileId: string): void {
  previewSrc.value = fileApi.url(fileId);
}

function pickImage(): void {
  imageInput.value?.click();
}

/**
 * 发图片：先把文件传到文件服务拿 fileId，再以 msgType=IMAGE 发一条消息。
 * 图片本体不进消息表（content 只存 fileId），客服端拼 /api/files/{fileId} 就能看到。
 */
async function onImagePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = ''; // 允许连续选同一张图
  if (!file || !chat) return;
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('图片超过 20MB，请压缩后再发');
    return;
  }
  uploadingImg.value = true;
  try {
    const uploaded = await unwrap(fileApi.upload(file));
    await chat.send(uploaded.fileId, 'IMAGE');
    scrollBottom();
  } catch (e) {
    ElMessage.error((e as Error).message || '图片发送失败');
  } finally {
    uploadingImg.value = false;
  }
}

/** 拉最近 10 笔订单：弹窗与「发订单给客服」共用一份，别各拉一次 */
async function loadOrders(): Promise<void> {
  ordersLoading.value = true;
  try {
    const res = await unwrap(orderApi.list({ page: 1, size: 10 }));
    myOrders.value = (res.list ?? []).map((o) => ({
      ...o,
      payableCent: Number(o.payableCent ?? 0),
      createdAt: Number(o.createdAt ?? 0),
    }));
  } catch {
    myOrders.value = [];
  } finally {
    ordersLoading.value = false;
  }
}

function firstTitle(o: OrderListItem): string {
  const first = (o.items ?? [])[0];
  const title = first?.title || '订单商品';
  const more = (o.items ?? []).length > 1 ? ` 等 ${(o.items ?? []).length} 件` : '';
  return `${title}${more}`;
}

/**
 * 把订单作为卡片消息发给客服。
 * content 只放摘要 JSON（订单号/状态/金额/首个商品名）：订单会改状态、会退款，
 * 卡片上的信息只用于「认出是哪一单」，真实信息双方都点进详情页看。
 */
async function sendOrderCard(o: OrderListItem): Promise<boolean> {
  if (!chat) return false;
  const payload = JSON.stringify({
    orderNo: o.orderNo,
    status: o.status ?? '',
    payableCent: Number(o.payableCent ?? 0),
    title: firstTitle(o),
    createdAt: Number(o.createdAt ?? 0),
  });
  sendingOrder.value = o.orderNo;
  try {
    await chat.send(payload, 'ORDER');
    scrollBottom();
    ElMessage.success('订单已发送给客服');
    return true;
  } catch (e) {
    ElMessage.error((e as Error).message || '发送失败');
    return false;
  } finally {
    sendingOrder.value = '';
  }
}

/* ---------------- 订单弹窗 / 表情 / 历史咨询 / 服务进度 ---------------- */
const orderDialogOpen = ref(false);
const emojiOpen = ref(false);

/** 常用表情：客户反馈「打字说不清情绪」，先给一屏够用的 */
const EMOJIS = [
  '😀', '😄', '😊', '🙂', '😉', '😍', '🤔', '😅',
  '😢', '😭', '😡', '😱', '👍', '👌', '🙏', '👏',
  '💪', '🎉', '❤️', '🔥', '⭐', '✅', '❓', '🕐',
];

function openOrderDialog(): void {
  orderDialogOpen.value = true;
  // 侧栏没开过时订单还没拉过；弹窗里也要同一份数据，缺了就补
  if (myOrders.value.length === 0) void loadOrders();
}

function sendOrderCardFromDialog(o: OrderListItem): void {
  void sendOrderCard(o).then((ok) => {
    // 发送成功才收起弹窗：失败要留在原地让用户重试，不然他得重新选一遍订单
    if (ok) orderDialogOpen.value = false;
  });
}

/** 非订单问题：不做「点一下直接发」，先填进输入框，客户可以再补两句自己的情况 */
function onFaqPick(question: string): void {
  insertText(question);
  orderDialogOpen.value = false;
}

function toggleEmoji(): void {
  emojiOpen.value = !emojiOpen.value;
}

function insertEmoji(emoji: string): void {
  draft.value += emoji;
  onDraftInput();
}

/** 拍照按需求暂不实现：必须给明确提示，别让人以为按钮坏了 */
function onCamera(): void {
  ElMessage.info('拍照功能即将上线，可先用「上传图片」发送照片');
}

const historyOpen = ref(false);
const progressOpen = ref(false);
const sessions = ref<SessionSummaryVO[]>([]);
const sessionsLoading = ref(false);

/** 历史咨询 / 服务进度共用一份会话摘要：一次请求两边都能用，别各拉一次 */
async function loadSessions(): Promise<void> {
  if (!customerId || sessionsLoading.value) return;
  sessionsLoading.value = true;
  try {
    const list = await unwrap(chatApi.sessions(customerId, 20));
    sessions.value = (list ?? []).map((s) => ({
      ...s,
      sessionId: Number(s.sessionId),
      staffId: s.staffId == null ? null : Number(s.staffId),
      createdAtMillis: Number(s.createdAtMillis ?? 0),
      lastActiveAtMillis: Number(s.lastActiveAtMillis ?? 0),
    }));
  } catch {
    sessions.value = [];
  } finally {
    sessionsLoading.value = false;
  }
}

function openHistory(): void {
  historyOpen.value = true;
  void loadSessions();
}

function openProgress(): void {
  progressOpen.value = true;
  void loadSessions();
}

/**
 * 服务进度弹窗要的「本次咨询」状态。
 * 会话摘要列表里能捞到接待客服与发起时间（WS 侧只给 status），捞不到就退回本地状态。
 */
const currentProgress = computed(() => {
  const sid = Number(sessionId.value ?? 0);
  if (!sid) return null;
  const cached = sessions.value.find((s) => Number(s.sessionId) === sid);
  return {
    sessionId: sid,
    status: String(chat?.status.value ?? (closed.value ? 'CLOSED' : 'ACTIVE')),
    staffId: cached?.staffId ?? null,
    createdAtMillis: Number(cached?.createdAtMillis ?? 0),
  };
});

function goBack(): void {
  if (window.history.length > 1) router.back();
  else router.push('/home');
}

watch(() => messages.value.length, scrollBottom);
watch(peerTyping, (on) => { if (on) scrollBottom(); });

onMounted(() => {
  if (!customerId) {
    ElMessage.warning('请先登录');
    router.replace('/login');
    return;
  }
  chat?.start();
  scrollBottom();
});
</script>

<style scoped>
.chat-page {
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  /* 与 .chat-card 同步：卡片高度变了这里跟着走，避免页内多留一层空白 */
  min-height: calc(100vh - 116px);
  display: flex;
  justify-content: center;
  padding: 18px 0 28px;
}
/* 聊天窗：单卡片居中（2026-11-24 去掉右侧订单侧栏后不再需要并排布局） */
.chat-shell {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 12px;
  width: 100%;
}

/* 整体做成「客服小窗」：圆角卡片 + 柔和阴影。
   2026-11-25：客服页改为沉浸式版式（无搜索区、无站点页脚）后整卡放大 —— 880 → 1040 宽。 */
.chat-card {
  width: 1040px;
  max-width: 100%;
  /* 高度预算里已不再有站点页脚：136 = 32 顶栏 + 58 主体内边距（18/40）+ 46 页内边距（18/28）。
     卡片底边正好落到视口底部，不出现滚动条。 */
  height: calc(100vh - 136px);
  min-height: 520px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 10px 30px rgba(18, 102, 79, 0.12);
  overflow: hidden;
}

/* ---------- 头部 ---------- */
.chat-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f2f3;
  background: #fff;
}
.chat-back {
  border: none;
  background: transparent;
  font-size: 18px;
  line-height: 1;
  color: var(--sent-text-2);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 6px;
}
.chat-back:hover {
  background: #f4f6f7;
  color: var(--sent-primary);
}
.chat-head__title {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.chat-head__title b {
  font-size: 15px;
  color: var(--sent-text);
}
.chat-state {
  font-size: 12px;
  color: #b5b9c0;
}
.chat-state--on {
  color: var(--sent-primary);
}
.chat-head__actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
}
.chat-head__actions .chat-head__link {
  margin-left: 0;
}
.chat-head__link {
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: 13px;
  color: var(--sent-primary);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 6px;
}
.chat-head__link:hover,
.chat-head__link--on {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
}

/* 历史咨询入口：标题栏下一条窄栏，不抢「查看订单 / 服务进度」的位置 */
.chat-history-bar {
  display: flex;
  justify-content: center;
  padding: 6px 16px;
  background: #fbfcfc;
  border-bottom: 1px solid #f4f6f7;
}
.chat-history-bar__link {
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: 12px;
  color: var(--sent-muted);
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 999px;
}
.chat-history-bar__link:hover {
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
}

/* ---------- 消息区 ---------- */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  background: linear-gradient(180deg, #fff8f3 0%, #fffdfb 42%, #ffffff 100%);
}
.chat-tip {
  text-align: center;
  font-size: 12px;
  color: #b9a99c;
  margin-bottom: 14px;
}
.chat-msg {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}
.chat-msg--self {
  justify-content: flex-end;
}
.chat-msg--other {
  justify-content: flex-start;
}
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  line-height: 1;
}
.avatar--staff {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  font-weight: 600;
}
/* 机器人头像：白底 + 绿色描边，和「客服」两个字区分开，一眼看出是小森在说话 */
.avatar--bot {
  background: #fff;
  border: 1px solid #e2efe9;
  font-size: 16px;
}
.avatar--me {
  background: var(--sent-accent-soft);
  color: var(--sent-accent-dark);
}
.chat-bubble {
  max-width: 74%;
  padding: 9px 13px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
}
.chat-msg--other .chat-bubble {
  background: #fff;
  color: var(--sent-text);
  border: 1px solid #f2e8e1;
  border-top-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}
.chat-msg--self .chat-bubble {
  background: var(--sent-primary);
  color: #fff;
  border-top-right-radius: 4px;
}
.chat-bubble--pending {
  opacity: 0.6;
}
/* 气泡里的发言人名字（迎宾气泡用）：小一号的主色字，不抢正文 */
.chat-bubble__name {
  display: block;
  margin-bottom: 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--sent-primary);
}
/* 图片气泡：去掉文字内边距，让图片自己撑满 */
.chat-bubble--img {
  padding: 4px;
  line-height: 0;
  max-width: 60%;
}
.chat-img {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  cursor: zoom-in;
  display: block;
}
.hidden-input {
  display: none;
}
/* 系统提示：居中浅灰底小字（会话已接入 / 已结束这类旁白）。
   机器人回复不再走这里——它是「有人格的话」，按普通气泡 + 机器人头像渲染 */
.chat-system {
  width: fit-content;
  max-width: 86%;
  margin: 10px auto 12px;
  padding: 5px 12px;
  border-radius: 10px;
  background: #f2f4f5;
  color: #7a8494;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
  word-break: break-word;
}
/* 机器人迎宾区：问候气泡 + 快捷问题芯片，直接复用正常气泡样式，视觉与真实回复一致 */
.chat-welcome {
  margin-bottom: 4px;
}
/* 快捷转人工：输入行下方一条窄提示，不占输入区主行 */
.chat-handoff {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.chat-handoff__btn {
  border: 1px solid var(--sent-primary);
  background: #fff;
  color: var(--sent-primary);
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  padding: 6px 14px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s;
}
.chat-handoff__btn:hover {
  background: var(--sent-primary);
  color: #fff;
}
.chat-handoff__hint {
  font-size: 11px;
  color: var(--sent-muted);
}
/* 服务评价卡（客服主动发 / 会话结束自动发） */
.chat-satisfy {
  margin: 8px 0 12px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  color: #874d00;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.chat-satisfy__hint { font-size: 11px; color: #b37f2b; }
.chat-satisfy__btns { display: flex; gap: 8px; margin-top: 4px; }
.chat-satisfy__btns button {
  flex: 1; padding: 6px 10px; border-radius: 6px; cursor: pointer;
  border: 1px solid #ffc069; background: #fff; color: #874d00; font-size: 12px;
}
.chat-satisfy__btns button:hover { background: #ffe7ba; }
.chat-satisfy__btns button:disabled { opacity: 0.6; cursor: not-allowed; }

/* 正在输入：三点跳动 */
.chat-bubble--typing {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #fff;
  border: 1px solid #f2e8e1;
}
.chat-bubble--typing em {
  font-style: normal;
  font-size: 12px;
  color: #9a8d83;
  margin-left: 4px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sent-accent);
  animation: typing-bounce 1.1s infinite ease-in-out;
}
.dot:nth-child(2) {
  animation-delay: 0.15s;
}
.dot:nth-child(3) {
  animation-delay: 0.3s;
}
@keyframes typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.45; }
  30% { transform: translateY(-3px); opacity: 1; }
}

/* ---------- 快捷问题（双列芯片） ---------- */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 2px 0 4px;
}
.quick-chip {
  text-align: left;
  padding: 10px 12px;
  border: 1px solid #f3d8c8;
  border-radius: 10px;
  background: #fffaf6;
  color: #8a5a3b;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.45;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}
.quick-chip:hover {
  border-color: var(--sent-accent);
  background: var(--sent-accent-soft);
  color: var(--sent-accent-dark);
}
@media (max-width: 720px) {
  .quick-grid {
    grid-template-columns: 1fr;
  }
}

/* ---------- 排队提示 ---------- */
.queue-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: #fff7e6;
  border-top: 1px solid #ffe1a8;
  color: #b8741a;
  font-size: 13px;
}
.queue-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e6a23c;
  flex: none;
  animation: queue-blink 1.2s ease-in-out infinite;
}
@keyframes queue-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}

/* 会话结束：灰色提示常驻，客户主动点「重新咨询」才恢复 */
.closed-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background: #f4f5f7;
  border-top: 1px solid #e6e8eb;
  color: #8a9099;
  font-size: 13px;
}
.closed-banner button {
  border: 1px solid #c8cdd4;
  background: #fff;
  color: #5f6773;
  border-radius: 6px;
  padding: 5px 12px;
  font-size: 12px;
  cursor: pointer;
}
.closed-banner button:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}

/* ---------- 输入区 ---------- */
.chat-input {
  border-top: 1px solid #f0f2f3;
  padding: 10px 14px 12px;
  background: #fff;
}
/* 一行排布：「发订单给客服」矩形 + 四个工具 + 输入框 + 发送 */
.chat-input__row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chat-input__tools {
  display: flex;
  flex: none;
  gap: 6px;
}
/* 「发订单给客服」矩形：宽度锁死成四个工具按钮的总宽（4×30 + 3×6 = 138px），
   改工具尺寸时这里要一起改，否则和按钮组对不齐 */
.send-order-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 138px;
  height: 30px;
  padding: 0 6px;
  border: 1px solid #ffe0c7;
  border-radius: 10px;
  background: linear-gradient(90deg, #fff6ee 0%, #fffdfb 100%);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  color: #a4571f;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.send-order-banner:hover {
  border-color: var(--sent-accent);
  box-shadow: 0 3px 10px rgba(255, 122, 26, 0.12);
}

/* 表情面板：一行 8 个，别铺满 12 列——格子太窄会把表情挤成小点，还难点中 */
.chat-emoji {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  margin-top: 8px;
  padding: 8px;
  border: 1px solid #eef1f2;
  border-radius: 10px;
  background: #fbfcfc;
}
.chat-emoji__item {
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  padding: 6px 0;
  border-radius: 6px;
  cursor: pointer;
}
.chat-emoji__item:hover {
  background: var(--sent-primary-soft);
}
.tool--on {
  border-color: var(--sent-primary);
  background: var(--sent-primary-soft);
}
.tool {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #ececec;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}
.tool:hover:not(:disabled) {
  border-color: var(--sent-accent);
}
.tool:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.chat-input__field {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 8px;
  border: 1px solid #ececec;
  border-radius: 10px;
  padding: 4px 4px 4px 12px;
}
.chat-input__field:focus-within {
  border-color: var(--sent-primary);
}
.chat-input__field input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  height: 32px;
  background: transparent;
  color: var(--sent-text);
}
.send-btn {
  border: none;
  border-radius: 8px;
  background: var(--sent-accent);
  color: #fff;
  font-size: 14px;
  padding: 8px 22px;
  cursor: pointer;
  transition: background 0.15s;
}
.send-btn:hover:not(:disabled) {
  background: var(--sent-accent-dark);
}
.send-btn:disabled {
  background: #f0c8ad;
  cursor: not-allowed;
}

/* 5 分钟时间分隔：微信式居中灰色小字 */
.chat-time {
  margin: 10px 0 6px;
  text-align: center;
  font-size: 11px;
  color: #a0a6b2;
  line-height: 1.4;
}

/* 窄屏：输入行实在塞不下时让输入框换到下一行，别把工具按钮挤变形 */
@media (max-width: 720px) {
  .chat-input__row {
    flex-wrap: wrap;
  }
  .chat-input__field {
    flex: 1 1 100%;
  }
}
</style>
