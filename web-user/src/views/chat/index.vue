<template>
  <div class="chat-page">
    <div class="chat-shell" :class="{ 'chat-shell--orders': ordersOpen }">
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
          <button
            type="button"
            class="chat-head__link"
            :class="{ 'chat-head__link--on': ordersOpen }"
            @click="toggleOrders"
          >
            查看订单 ›
          </button>
        </header>

        <!-- 消息区 -->
        <div ref="listRef" class="chat-body">
          <div class="chat-tip">想咨询订单、定制、发票或退换货，直接说就好～</div>

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
            <div v-else class="chat-msg" :class="m.self ? 'chat-msg--self' : 'chat-msg--other'">
              <div v-if="!m.self" class="avatar avatar--staff">客服</div>
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

          <!-- 快捷问题：首屏没有消息时展示，点一下直接发出去 -->
          <div v-if="messages.length === 0" class="quick-list">
            <button
              v-for="q in QUICK_QUESTIONS"
              :key="q"
              class="quick-item"
              @click="askQuick(q)"
            >
              <span class="quick-item__dot">?</span>
              <span class="quick-item__text">{{ q }}</span>
              <span class="quick-item__arrow">›</span>
            </button>
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

        <!-- 输入区 -->
        <footer class="chat-input">
          <div class="chat-input__tools">
            <button class="tool" title="发送订单" @click="toggleOrders">📦</button>
            <button class="tool" title="发送图片" :disabled="queued || uploadingImg" @click="pickImage">
              {{ uploadingImg ? '…' : '🖼' }}
            </button>
            <button class="tool" title="常用问题" @click="insertText('请问这件商品有货吗？')">💬</button>
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
        </footer>
      </div>

      <!-- 右侧订单侧栏：点「查看订单」展开，选中一单即可发给客服 -->
      <aside v-if="ordersOpen" class="chat-orders">
        <header class="chat-orders__head">
          <b>我的订单</b>
          <span class="chat-orders__hint">{{ queued ? '客服接入后可发送' : '选中一单发给客服' }}</span>
          <button type="button" class="chat-orders__close" title="收起" @click="ordersOpen = false">×</button>
        </header>

        <div v-if="ordersLoading" class="chat-orders__empty">加载中…</div>
        <div v-else-if="myOrders.length === 0" class="chat-orders__empty">
          还没有订单，去逛逛吧
        </div>
        <div v-else class="chat-orders__list">
          <div v-for="o in myOrders" :key="o.orderNo" class="chat-order-row">
            <div class="chat-order-row__top">
              <span class="chat-order-row__no">{{ o.orderNo }}</span>
              <span class="chat-order-row__status">{{ statusLabel(o.status) }}</span>
            </div>
            <div class="chat-order-row__title">{{ firstTitle(o) }}</div>
            <div class="chat-order-row__foot">
              <span class="chat-order-row__amount">¥{{ o.payableYuan || fmtCent(Number(o.payableCent ?? 0)) }}</span>
              <button
                type="button"
                class="chat-order-row__send"
                :disabled="queued || sendingOrder === o.orderNo"
                :title="queued ? '客服接入后可发送订单' : '发给客服'"
                @click="sendOrderCard(o)"
              >
                {{ sendingOrder === o.orderNo ? '发送中…' : '发给客服' }}
              </button>
            </div>
          </div>
        </div>

        <footer class="chat-orders__foot">
          <router-link class="chat-orders__all" to="/orders">查看全部订单 ›</router-link>
        </footer>
      </aside>
    </div>

    <!-- 点图片看大图（Element Plus 全局注册，无需再 import） -->
    <el-image-viewer
      v-if="previewSrc"
      :url-list="[previewSrc]"
      @close="previewSrc = ''"
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
 * 订单协同（2026-11-15 用户要求）：
 * - 顶部「查看订单」/ 输入区 📦 展开右侧订单侧栏（不离开聊天，不跳页）；
 * - 选中一单点「发给客服」，把订单摘要以 msgType=ORDER 发出，双方渲染成同一张订单卡片；
 * - 客服端另有右侧「顾客历史订单」面板，可看到该顾客最近买过什么。
 */
import { nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtCent, fmtChatTime, shouldShowTimeDivider, statusLabel } from '@sent/shared';
import { useChatSession } from '@/composables/useChatSession';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { fileApi } from '@/api/file';
import { orderApi, type OrderListItem } from '@/api/order';
import { chatApi } from '@/api/chat';
import { unwrap } from '@/utils/http';
import ChatOrderCard from '@/components/ChatOrderCard.vue';

const router = useRouter();
const auth = useAuthStore();
const draft = ref('');
const sending = ref(false);
const listRef = ref<HTMLElement | null>(null);
const imageInput = ref<HTMLInputElement | null>(null);
const uploadingImg = ref(false);

const customerId = auth.userId;
const chat = customerId ? useChatSession(customerId, false) : null;
const messages = chat ? chat.messages : ref([]);
const online = chat ? chat.online : ref(false);
const queued = chat ? chat.queued : ref(false);
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
  const labels: Record<string, string> = { SATISFIED: '满意', NORMAL: '一般', UNSATISFIED: '不满意' };
  return labels[score] ?? score;
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

/** 自己气泡上的头像文字：商城端不缓存昵称，统一用「我」 */
const meBadge = '我';

/* ---------------- 右侧订单侧栏 ---------------- */
const ordersOpen = ref(false);
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
    draft.value = '';
    scrollBottom();
  } catch (e) {
    ElMessage.error((e as Error).message || '发送失败');
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

/** 展开/收起订单侧栏：首次展开才拉数据，之后复用（订单状态变化由用户主动刷新） */
async function toggleOrders(): Promise<void> {
  ordersOpen.value = !ordersOpen.value;
  if (ordersOpen.value && myOrders.value.length === 0) {
    await loadOrders();
  }
}

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
async function sendOrderCard(o: OrderListItem): Promise<void> {
  if (!chat) return;
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
  } catch (e) {
    ElMessage.error((e as Error).message || '发送失败');
  } finally {
    sendingOrder.value = '';
  }
}

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
  min-height: calc(100vh - 240px);
  display: flex;
  justify-content: center;
  padding: 18px 0 28px;
}
/* 聊天窗 + 订单侧栏并排：侧栏展开时聊天窗自动收窄，不跳页 */
.chat-shell {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 12px;
  width: 100%;
}

/* 整体做成「客服小窗」：圆角卡片 + 柔和阴影 */
.chat-card {
  width: 760px;
  max-width: 100%;
  height: calc(100vh - 260px);
  min-height: 520px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 10px 30px rgba(18, 102, 79, 0.12);
  overflow: hidden;
  transition: width 0.18s ease;
}
.chat-shell--orders .chat-card {
  width: 540px;
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
.chat-head__link {
  margin-left: auto;
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
.chat-system {
  margin: 6px 0 12px;
  text-align: center;
  font-size: 12px;
  color: #8a94a6;
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

/* ---------- 快捷问题 ---------- */
.quick-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 6px;
}
.quick-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border: 1px solid #f3d8c8;
  border-radius: 10px;
  background: #fffaf6;
  color: #8a5a3b;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.quick-item:hover {
  border-color: var(--sent-accent);
  background: var(--sent-accent-soft);
  color: var(--sent-accent-dark);
  transform: translateX(2px);
}
.quick-item__dot {
  width: 18px;
  height: 18px;
  flex: none;
  border-radius: 50%;
  background: var(--sent-accent);
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.quick-item__text {
  flex: 1;
}
.quick-item__arrow {
  color: #d9b49b;
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
.chat-input__tools {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.tool {
  width: 30px;
  height: 30px;
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

/* ---------- 右侧订单侧栏 ---------- */
.chat-orders {
  width: 320px;
  flex: none;
  height: calc(100vh - 260px);
  min-height: 520px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 10px 30px rgba(18, 102, 79, 0.1);
  overflow: hidden;
}
.chat-orders__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f2f3;
}
.chat-orders__head b {
  font-size: 14px;
  color: var(--sent-text);
}
.chat-orders__hint {
  font-size: 11px;
  color: var(--sent-muted);
}
.chat-orders__close {
  margin-left: auto;
  border: none;
  background: transparent;
  font-size: 18px;
  line-height: 1;
  color: var(--sent-muted);
  cursor: pointer;
}
.chat-orders__close:hover {
  color: var(--sent-primary);
}
.chat-orders__list {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.chat-orders__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.chat-order-row {
  border: 1px solid #edf0f2;
  border-radius: 10px;
  padding: 10px 11px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.chat-order-row:hover {
  border-color: var(--sent-primary);
  box-shadow: 0 4px 12px rgba(18, 102, 79, 0.08);
}
.chat-order-row__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.chat-order-row__no {
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--sent-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-order-row__status {
  flex: none;
  font-size: 11px;
  color: var(--sent-primary);
}
.chat-order-row__title {
  margin: 6px 0 8px;
  font-size: 13px;
  line-height: 1.45;
  color: var(--sent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.chat-order-row__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.chat-order-row__amount {
  font-size: 14px;
  font-weight: 700;
  color: var(--sent-accent-dark);
}
.chat-order-row__send {
  border: 1px solid var(--sent-primary);
  background: #fff;
  color: var(--sent-primary);
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  padding: 6px 10px;
  border-radius: 6px;
  cursor: pointer;
}
.chat-order-row__send:hover:not(:disabled) {
  background: var(--sent-primary);
  color: #fff;
}
.chat-order-row__send:disabled {
  opacity: 0.5;
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
.chat-orders__foot {
  padding: 10px 14px;
  border-top: 1px solid #f0f2f3;
  text-align: center;
}
.chat-orders__all {
  font-size: 12px;
  color: var(--sent-primary);
}

@media (max-width: 1100px) {
  .chat-shell {
    flex-direction: column;
  }
  .chat-card,
  .chat-shell--orders .chat-card {
    width: 100%;
  }
  .chat-orders {
    width: 100%;
    height: auto;
    max-height: 360px;
  }
}
</style>
