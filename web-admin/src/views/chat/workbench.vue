<template>
  <div>
    <h2 class="sent-page-title">客服工作台</h2>

    <div class="sent-panel">
      <div class="sent-form-row">
        <el-tag :type="connected ? 'success' : 'info'" effect="dark" size="small">
          {{ connected ? 'WebSocket 已连接' : 'WebSocket 未连接' }}
        </el-tag>
        <span class="sent-hint">在线客服 {{ onlineCount }} · 我的并发 {{ activeRows.length }}/{{ maxSessions }}</span>
        <el-input-number v-model="staffId" :min="1" :controls="false" style="width: 130px" />
        <el-select v-model="presence" style="width: 120px" @change="changePresence">
          <el-option label="在线" :value="PresenceStatus.ONLINE" />
          <el-option label="忙碌" :value="PresenceStatus.BUSY" />
          <el-option label="离线" :value="PresenceStatus.OFFLINE" />
        </el-select>
        <el-button @click="reconnect">重连</el-button>
        <el-button @click="loadOpen">刷新列表</el-button>
        <el-button @click="loadWorkload">刷新工作量</el-button>
        <!-- 今日完成接待数：按本地自然日统计，跨零点自动归零（每天刷新） -->
        <div class="today-stat" :title="`统计日期 ${todayStats.date || '--'}`">
          <span class="today-stat__label">今日完成接待</span>
          <b class="today-stat__num">{{ todayStats.closedCount }}</b>
          <span class="today-stat__unit">次</span>
        </div>
      </div>
      <p v-if="workload.length" class="sent-hint workload">
        接待量：{{ workload.map(w => `${w.staffId}=${w.activeSessions}/${w.maxSessions ?? maxSessions}（${w.presence ?? '-'}）`).join(' · ') }}
      </p>
    </div>

    <el-row :gutter="16" class="workbench">
      <!-- 左：进行中 / 排队中 -->
      <el-col :xs="24" :lg="7">
        <div class="sent-panel list-panel">
          <h3 class="section-title">进行中 <span class="count">{{ activeRows.length }}</span></h3>
          <el-empty v-if="!activeRows.length" description="暂无进行中会话" :image-size="50" />
          <div
            v-for="row in activeRows"
            :key="row.sessionId"
            class="session-item"
            :class="{ current: isOpen(row.sessionId) }"
            @click="openSession(row)"
          >
            <div class="line1">
              <strong>会话 #{{ row.sessionId }}</strong>
              <StatusTag :status="row.status" />
            </div>
            <div class="line2">客户 {{ row.customerId }} · 已等待 {{ waitText(row.waitingSinceMillis) }}</div>
          </div>
        </div>

        <div class="sent-panel list-panel">
          <h3 class="section-title">排队中 <span class="count">{{ waitingRows.length }}</span></h3>
          <el-empty v-if="!waitingRows.length" description="暂无排队客户" :image-size="50" />
          <div
            v-for="row in waitingRows"
            :key="row.sessionId"
            class="session-item queue-item"
            :class="{ current: isOpen(row.sessionId) }"
            @click="openSession(row)"
          >
            <div class="line1">
              <strong>会话 #{{ row.sessionId }}</strong>
              <el-tag size="small" type="warning">第 {{ row.queuePosition }} 位</el-tag>
            </div>
            <div class="line2">客户 {{ row.customerId }} · 已排队 {{ waitText(row.waitingSinceMillis) }}</div>
          </div>
        </div>
      </el-col>

      <!-- 右：多 Tab 聊天区（最多 3 个） -->
      <el-col :xs="24" :lg="17">
        <div class="sent-panel chat-panel">
          <el-empty v-if="!tabs.length" description="从左侧点击会话打开聊天（最多 3 个）" :image-size="70" />
          <el-tabs v-else v-model="activeTab" type="card" closable @tab-remove="closeTab">
            <el-tab-pane
              v-for="tab in tabs"
              :key="String(tab.sessionId)"
              :name="String(tab.sessionId)"
            >
              <template #label>
                <span class="tab-label">
                  #{{ tab.sessionId }}
                  <el-tag v-if="tab.status === ChatSessionStatus.WAITING" size="small" type="warning">排队中</el-tag>
                </span>
              </template>

              <div class="tab-body">
                <div class="message-box">
                  <div
                    v-for="m in messagesOf(tab.sessionId)"
                    :key="m.key"
                    class="message-line"
                    :class="{ mine: m.senderType === SenderType.STAFF }"
                  >
                    <span v-if="m.msgType === 'IMAGE'" class="bubble bubble--img">
                      <img
                        class="bubble-img"
                        :src="fileApi.url(m.content)"
                        alt="客户图片"
                        @click="previewImage(m.content)"
                      />
                      <small v-if="m.pending">· 发送中</small>
                    </span>
                    <!-- 顾客发来的订单卡片：点「查看订单详情」跳管理端订单详情核对商品与金额 -->
                    <ChatOrderCard
                      v-else-if="m.msgType === 'ORDER'"
                      :content="m.content"
                      :mine="m.senderType === SenderType.STAFF"
                    />
                    <span v-else class="bubble">
                      {{ m.content }}
                      <small v-if="m.pending">· 发送中</small>
                    </span>
                  </div>
                  <el-empty v-if="!messagesOf(tab.sessionId).length" description="暂无消息" :image-size="50" />
                  <!-- 客户正在输入：收到 TYPING 帧亮 3s，无新帧自动熄灭 -->
                  <div v-if="typingSessions.has(tab.sessionId)" class="typing-hint">
                    <span class="typing-dot" /><span class="typing-dot" /><span class="typing-dot" />
                    客户正在输入…
                  </div>
                </div>

                <div class="send-row">
                  <el-button
                    :disabled="!connected || uploadingImg"
                    :loading="uploadingImg"
                    title="发送图片"
                    @click="pickImage(tab.sessionId)"
                  >
                    🖼 图片
                  </el-button>
                  <el-input
                    v-model="drafts[tab.sessionId]"
                    placeholder="输入消息后回车发送"
                    @input="notifyTyping(tab.sessionId)"
                    @keyup.enter="sendMessage(tab)"
                  />
                  <el-button type="primary" :disabled="!connected" @click="sendMessage(tab)">发送</el-button>
                </div>

                <div class="sent-form-row tab-actions">
                  <el-button
                    v-if="tab.status === ChatSessionStatus.WAITING"
                    type="primary"
                    @click="accept(tab)"
                  >
                    接待
                  </el-button>
                  <el-button type="danger" @click="closeSession(tab)">结束会话</el-button>
                  <span class="sent-hint">
                    会话 #{{ tab.sessionId }} · 客户 {{ tab.customerId }}
                    <template v-if="tab.status === ChatSessionStatus.WAITING">
                      · 当前排队第 {{ positionOf(tab.sessionId) }} 位
                    </template>
                  </span>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>

          <!-- 右侧：当前 Tab 所属顾客的历史订单（客服接待时顺手核对买了什么） -->
          <aside v-if="tabs.length" class="cust-orders">
            <header class="cust-orders__head">
              <b>顾客历史订单</b>
              <span class="cust-orders__cid">客户 {{ activeCustomerId || '--' }}</span>
            </header>
            <div v-if="custOrdersLoading" class="cust-orders__hint">加载中…</div>
            <div v-else-if="!custOrders.length" class="cust-orders__hint">该顾客暂无订单</div>
            <div v-else class="cust-orders__list">
              <div
                v-for="o in custOrders"
                :key="o.orderNo"
                class="cust-order"
                :class="{ 'cust-order--active': activeOrderNo === o.orderNo }"
                @click="openOrderDetail(o)"
              >
                <div class="cust-order__top">
                  <span class="cust-order__no">{{ o.orderNo }}</span>
                  <StatusTag :status="o.status" />
                </div>
                <div class="cust-order__foot">
                  <span class="cust-order__time">{{ fmtDateTime(Number(o.createdAt ?? 0)) }}</span>
                  <b class="cust-order__amount">
                    ¥{{ o.payableYuan || fmtCent(Number(o.payableCent ?? 0)) }}
                  </b>
                </div>
              </div>
            </div>
            <p class="cust-orders__tip">点订单可查看详情与商品行</p>
          </aside>
        </div>
      </el-col>
    </el-row>

    <input ref="imageInput" class="hidden-input" type="file" accept="image/*" @change="onImagePicked" />

    <!-- 点图片看大图 -->
    <el-image-viewer v-if="previewSrc" :url-list="[previewSrc]" @close="previewSrc = ''" />
  </div>
</template>

<script setup lang="ts">
/**
 * 客服工作台（多 Tab 版）：左侧「进行中 / 排队中」，右侧最多 3 个会话 Tab（与后端并发上限对齐）。
 *
 * - 打开 Tab：点左侧会话行；已达 3 个时拒绝并提示（前端硬限，后端 403/排队兜底）。
 * - 结束会话：调 close 接口 → 后端自动补位（把排队第一的客户转 ACTIVE 并推送 ASSIGNED）→ 刷新左侧。
 * - 实时：WebSocket 帧按会话号落到对应 Tab；非 Tab 会话的消息只触发列表刷新（3s 节流）。
 */
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtCent, fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import {
  chatApi,
  orderApi,
  type AdminCustomerOrderVO,
  type OpenSessionRow,
  type TodayStatsVO,
  type WorkloadVO,
} from '@/api';
import { fileApi } from '@/api/file';
import { usePermissionStore } from '@/store/modules/permission';
import { ChatSessionStatus, PresenceStatus, SenderType, statusLabel } from '@sent/shared';
import { useChatSocket, type IncomingAck, type IncomingMessage } from '@/composables/useChatSocket';
import StatusTag from '@/components/StatusTag.vue';
import ChatOrderCard from '@/components/ChatOrderCard.vue';

interface UiMessage {
  key: string;
  clientMsgId: string;
  senderType: string;
  /** TEXT / IMAGE；IMAGE 时 content 是 fileId */
  msgType: string;
  content: string;
  pending?: boolean;
}

interface OpenTab {
  sessionId: number;
  status: string;
  staffId: number | null;
  customerId: number;
}

/** 与后端 AgentPresence.DEFAULT_MAX_SESSIONS / staff_presence.max_sessions 对齐 */
const MAX_TABS = 3;

const permission = usePermissionStore();
const staffId = ref<number>(Number(permission.me?.userId ?? 0) || 1);
const presence = ref<string>(PresenceStatus.ONLINE);
const onlineCount = ref(0);
const workload = ref<WorkloadVO[]>([]);
const maxSessions = ref<number>(MAX_TABS);

const activeRows = ref<OpenSessionRow[]>([]);
const waitingRows = ref<OpenSessionRow[]>([]);

const tabs = ref<OpenTab[]>([]);
const activeTab = ref<string>('');
const drafts = reactive<Record<number, string>>({});
const messageStore = reactive<Record<number, UiMessage[]>>({});

const socket = useChatSocket({
  onMessage: onFrame,
  onAck,
  onTyping: onPeerTyping,
  onConnected: (count) => { onlineCount.value = count; },
});
const { connected, connect, close: closeSocket, send, sendTyping } = socket;

/** 今日完成接待数（顶栏右侧，跨零点自动归零） */
const todayStats = ref<TodayStatsVO>({ staffId: 0, date: '', closedCount: 0 });

/** 正在输入的会话集合：收到 TYPING 帧加入，3s 无新帧移除 */
const typingSessions = reactive(new Set<number>());
const typingTimers = new Map<number, ReturnType<typeof setTimeout>>();
/** 每个会话「我正在输入」的上报节流时间戳（2s） */
const typingSentAt = new Map<number, number>();

function onPeerTyping(sessionId: number): void {
  const id = Number(sessionId);
  if (!id) return;
  typingSessions.add(id);
  const existing = typingTimers.get(id);
  if (existing) clearTimeout(existing);
  typingTimers.set(id, setTimeout(() => {
    typingSessions.delete(id);
    typingTimers.delete(id);
  }, 3000));
}

/**
 * 客服打字 → 上报给客户。
 * 节流 1s：太短会每个按键发一帧，太长（2s）会让「停一下再打」的那次提示丢帧，
 * 对方看起来像没反应。弱语义、不重试。
 */
function notifyTyping(sessionId: number): void {
  const id = Number(sessionId);
  const now = Date.now();
  if (now - (typingSentAt.get(id) ?? 0) < 1000) return;
  typingSentAt.set(id, now);
  sendTyping(id);
}

async function loadTodayStats(): Promise<void> {
  try {
    const data = await unwrap(chatApi.todayStats(meStaffId.value));
    todayStats.value = {
      staffId: Number(data?.staffId ?? meStaffId.value),
      date: String(data?.date ?? ''),
      closedCount: Number(data?.closedCount ?? 0),
    };
  } catch {
    // 统计失败不打断工作台
  }
}

const meStaffId = computed(() => Number(staffId.value));

/* ------------------------------------------------------------------
 * 右侧「顾客历史订单」：跟着当前 Tab 的客户走
 * ------------------------------------------------------------------ */
const router = useRouter();
const custOrders = ref<AdminCustomerOrderVO[]>([]);
const custOrdersLoading = ref(false);
/** 刚点过的那一单：给侧栏一点视觉反馈，避免点完跳走再回来找不到位置 */
const activeOrderNo = ref('');

const activeCustomerId = computed(() => {
  const tab = tabs.value.find((t) => String(t.sessionId) === activeTab.value);
  return tab ? Number(tab.customerId) : 0;
});

async function loadCustomerOrders(customerId: number): Promise<void> {
  if (!customerId) {
    custOrders.value = [];
    return;
  }
  custOrdersLoading.value = true;
  try {
    const rows = await unwrap(orderApi.customerOrders(customerId, 10));
    // Long 序列化成字符串：金额与时间统一 Number() 归一后再交给格式化函数
    custOrders.value = (rows ?? []).map((r) => ({
      ...r,
      payableCent: Number(r.payableCent ?? 0),
      createdAt: Number(r.createdAt ?? 0),
    }));
  } catch {
    // 拉不到订单不影响接待，静默空态即可
    custOrders.value = [];
  } finally {
    custOrdersLoading.value = false;
  }
}

watch(activeCustomerId, (id) => {
  activeOrderNo.value = '';
  void loadCustomerOrders(id);
}, { immediate: true });

function openOrderDetail(o: AdminCustomerOrderVO): void {
  activeOrderNo.value = o.orderNo;
  router.push(`/order/detail/${o.orderNo}`);
}

function messagesOf(sessionId: number): UiMessage[] {
  if (!messageStore[sessionId]) messageStore[sessionId] = [];
  return messageStore[sessionId];
}

function isOpen(sessionId: number): boolean {
  return tabs.value.some((t) => t.sessionId === sessionId);
}

function positionOf(sessionId: number): number {
  const row = waitingRows.value.find((r) => r.sessionId === sessionId);
  return row?.queuePosition ?? 0;
}

function waitText(since: number): string {
  if (!since) return '--';
  const minutes = Math.max(0, Math.floor((Date.now() - Number(since)) / 60_000));
  return minutes < 1 ? '不足 1 分钟' : `${minutes} 分钟`;
}

function pushMessage(sessionId: number, message: UiMessage): void {
  const list = messagesOf(sessionId);
  if (message.clientMsgId) {
    const index = list.findIndex((item) => !!message.clientMsgId && item.clientMsgId === message.clientMsgId);
    if (index >= 0) {
      list[index] = { ...list[index], pending: false };
      return;
    }
  }
  list.push(message);
}

function onFrame(frame: IncomingMessage): void {
  const sessionId = Number(frame.sessionId);
  if (isOpen(sessionId)) {
    pushMessage(sessionId, {
      key: frame.clientMsgId || `seq_${frame.seq}`,
      clientMsgId: frame.clientMsgId,
      senderType: frame.senderType,
      msgType: frame.msgType || 'TEXT',
      content: frame.content,
      pending: false,
    });
    return;
  }
  // 未打开 Tab 的会话（含排队客户的 QUEUE 广播）：刷新左侧列表让新会话冒出来
  scheduleListRefresh();
}

function onAck(ack: IncomingAck): void {
  if (ack.event === 'REJECTED' || ack.code) {
    ElMessage.error(`消息被拒绝：${ack.reason ?? `code ${ack.code}`}`);
    return;
  }
  if (!ack.clientMsgId) return;
  for (const sessionId of Object.keys(messageStore)) {
    const list = messageStore[Number(sessionId)];
    const index = list.findIndex((item) => item.clientMsgId === ack.clientMsgId);
    if (index >= 0) {
      list[index] = { ...list[index], pending: false };
      return;
    }
  }
}

/** 列表刷新节流：3s 内最多一次，避免排队期间每个消息帧都打接口 */
let refreshTimer: ReturnType<typeof setTimeout> | undefined;
let refreshedAt = 0;
function scheduleListRefresh(): void {
  if (refreshTimer) return;
  refreshTimer = setTimeout(() => {
    refreshTimer = undefined;
    refreshedAt = Date.now();
    void loadOpen();
  }, Math.max(0, 3000 - (Date.now() - refreshedAt)));
}

/** 后端把 Long 序列化成字符串（防 JS 精度丢失），列表行必须先归一成 number 再进 Tab，
 *  否则 `isOpen(Number(frame.sessionId))` 与字符串 sessionId 永不相等，实时消息会被静默丢弃。 */
function normalizeRow(row: any): OpenSessionRow {
  return {
    sessionId: Number(row.sessionId),
    status: String(row.status ?? ''),
    staffId: row.staffId === null || row.staffId === undefined ? null : Number(row.staffId),
    customerId: Number(row.customerId),
    waitingSinceMillis: Number(row.waitingSinceMillis ?? 0),
    queuePosition: Number(row.queuePosition ?? 0),
  };
}

async function loadOpen(): Promise<void> {
  try {
    const data = await unwrap(chatApi.openSessions(meStaffId.value));
    activeRows.value = (data?.active ?? []).map(normalizeRow);
    waitingRows.value = (data?.waiting ?? []).map(normalizeRow);
    syncTabs();
  } catch (error) {
    activeRows.value = [];
    waitingRows.value = [];
    ElMessage.error((error as Error)?.message || '会话列表加载失败');
  }
}

/** 列表刷新后把 Tab 的状态/接待人指回最新值；已关闭/不再可见的 Tab 直接移除 */
function syncTabs(): void {
  const rows = [...activeRows.value, ...waitingRows.value];
  const kept: OpenTab[] = [];
  for (const tab of tabs.value) {
    const fresh = rows.find((row) => row.sessionId === tab.sessionId);
    if (fresh) {
      kept.push({ ...tab, status: fresh.status, staffId: fresh.staffId, customerId: fresh.customerId });
      continue;
    }
    ElMessage.info(`会话 #${tab.sessionId} 已结束`);
    delete messageStore[tab.sessionId];
  }
  tabs.value = kept;
  if (!kept.some((t) => String(t.sessionId) === activeTab.value)) {
    activeTab.value = kept.length ? String(kept[0].sessionId) : '';
  }
}

async function loadWorkload(): Promise<void> {
  try {
    workload.value = await unwrap(chatApi.workload());
    const mine = workload.value.find((w) => Number(w.staffId) === meStaffId.value);
    if (mine?.maxSessions) maxSessions.value = Number(mine.maxSessions);
  } catch {
    workload.value = [];
  }
}

async function changePresence(): Promise<void> {
  try {
    await unwrap(chatApi.presence(meStaffId.value, presence.value));
    ElMessage.success(`在线状态已设为 ${statusLabel(presence.value)}`);
    await Promise.all([loadOpen(), loadWorkload()]);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '状态更新失败');
  }
}

/**
 * 打开（或聚焦）一个会话 Tab。
 * 硬限 3 个：与后端并发上限一致，超出时提示而不是静默排队——一个坐席同时盯 4 个窗口只会拖慢响应。
 */
async function openSession(row: OpenSessionRow): Promise<void> {
  const existing = tabs.value.find((t) => t.sessionId === row.sessionId);
  if (existing) {
    activeTab.value = String(row.sessionId);
    return;
  }
  if (tabs.value.length >= MAX_TABS) {
    ElMessage.warning(`最多同时打开 ${MAX_TABS} 个会话，请先结束一个`);
    return;
  }
  tabs.value.push({
    sessionId: row.sessionId,
    status: row.status,
    staffId: row.staffId,
    customerId: row.customerId,
  });
  activeTab.value = String(row.sessionId);
  await loadHistory(row.sessionId);
}

/**
 * 拉历史并「合并」进消息列表。
 * <p>不能整体替换：打开 Tab 的瞬间如果正好有 WS 推送进来（客户刚发消息），
 * 替换会把那条实时消息抹掉，表现为「第一条消息看不到」。这里按 clientMsgId / key 去重合并。
 */
async function loadHistory(sessionId: number): Promise<void> {
  try {
    const history = await unwrap(chatApi.history(sessionId));
    const fetched: UiMessage[] = (history ?? []).map((item: any) => ({
      key: String(item.messageId),
      clientMsgId: item.clientMsgId ?? '',
      senderType: item.senderType,
      msgType: item.msgType ?? 'TEXT',
      content: item.content,
    }));
    const existing = messageStore[sessionId] ?? [];
    const merged = [...fetched];
    for (const item of existing) {
      const duplicated = merged.some((m) =>
        (!!m.clientMsgId && m.clientMsgId === item.clientMsgId) || m.key === item.key);
      if (!duplicated) merged.push(item);
    }
    messageStore[sessionId] = merged;
  } catch (error) {
    ElMessage.error((error as Error)?.message || '历史消息加载失败');
  }
}

function closeTab(name: string | number): void {
  const sessionId = Number(name);
  tabs.value = tabs.value.filter((t) => t.sessionId !== sessionId);
  delete messageStore[sessionId];
  if (!tabs.value.length) {
    activeTab.value = '';
  } else if (activeTab.value === String(sessionId)) {
    activeTab.value = String(tabs.value[tabs.value.length - 1].sessionId);
  }
}

function sendMessage(tab: OpenTab): void {
  const content = (drafts[tab.sessionId] ?? '').trim();
  if (!content) return;
  const clientMsgId = send(tab.sessionId, content, 'TEXT');
  if (!clientMsgId) {
    ElMessage.warning('WebSocket 未连接，请稍候或点击重连');
    return;
  }
  pushMessage(tab.sessionId, {
    key: clientMsgId,
    clientMsgId,
    senderType: SenderType.STAFF,
    msgType: 'TEXT',
    content,
    pending: true,
  });
  drafts[tab.sessionId] = '';
}

/* ---------------- 客服发图片 ---------------- */
const previewSrc = ref('');
const uploadingImg = ref(false);
/** 当前要发图的会话号：隐藏 input 只有一个，靠它记住目标 Tab */
const imageTargetSession = ref<number | null>(null);
const imageInput = ref<HTMLInputElement | null>(null);

function previewImage(fileId: string): void {
  previewSrc.value = fileApi.url(fileId);
}

function pickImage(sessionId: number): void {
  imageTargetSession.value = sessionId;
  imageInput.value?.click();
}

/**
 * 客服发图片：先上传拿 fileId，再以 msgType=IMAGE 通过 WS 发一条消息。
 * 客户侧收到后按 msgType 渲染成图片气泡。
 */
async function onImagePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  const sessionId = imageTargetSession.value;
  input.value = '';
  if (!file || !sessionId) return;
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('图片超过 20MB，请压缩后再发');
    return;
  }
  uploadingImg.value = true;
  try {
    const uploaded = await unwrap(fileApi.upload(file));
    const clientMsgId = send(sessionId, uploaded.fileId, 'IMAGE');
    if (!clientMsgId) {
      ElMessage.warning('WebSocket 未连接，请稍候或点击重连');
      return;
    }
    pushMessage(sessionId, {
      key: clientMsgId,
      clientMsgId,
      senderType: SenderType.STAFF,
      msgType: 'IMAGE',
      content: uploaded.fileId,
      pending: true,
    });
  } catch (e) {
    ElMessage.error((e as Error).message || '图片发送失败');
  } finally {
    uploadingImg.value = false;
  }
}

async function accept(tab: OpenTab): Promise<void> {
  try {
    await unwrap(chatApi.accept(tab.sessionId, meStaffId.value));
    ElMessage.success(`已接待会话 #${tab.sessionId}`);
    await loadOpen();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '接待失败');
  }
}

/** 结束会话：关闭后后端自动补位排队第一的客户，这里只需刷出新状态 */
async function closeSession(tab: OpenTab): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定结束会话 #${tab.sessionId}？结束后会自动接入排队中的下一位客户。`, '结束会话', {
      confirmButtonText: '确认结束',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    const result = await unwrap(chatApi.close(tab.sessionId, 'DONE'));
    ElMessage.success(result?.promotedSessionId
      ? `会话已结束，已自动接入排队客户 #${result.promotedSessionId}`
      : '会话已结束');
    closeTab(tab.sessionId);
    await Promise.all([loadOpen(), loadTodayStats()]);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '结束会话失败');
  }
}

function reconnect(): void {
  closeSocket();
  connect();
}

/**
 * 打开工作台即上报在线状态：后端建会话时按「在线坐席」自动分配，
 * 不报状态的话客户进线只能落 WAITING，队列永远补不上人。
 */
async function reportPresence(): Promise<void> {
  const id = Number(permission.me?.userId ?? 0);
  if (!Number.isFinite(id) || id <= 0) return;
  staffId.value = id;
  try {
    await unwrap(chatApi.presence(id, presence.value));
  } catch {
    // 静默：上报失败不影响列表与实时消息
  }
}

onMounted(async () => {
  connect();
  void reportPresence();
  await Promise.all([loadOpen(), loadWorkload(), loadTodayStats()]);
});

onUnmounted(() => {
  if (refreshTimer) clearTimeout(refreshTimer);
});
</script>

<style scoped>
.section-title { margin: 0 0 12px; font-size: 15px; color: #33463f; display: flex; align-items: center; gap: 8px; }
.count { color: #12664f; font-weight: 600; }
.workload { margin: 8px 0 0; }
.workbench { margin-top: 16px; }
.list-panel { min-height: 180px; margin-bottom: 16px; }
.session-item {
  border: 1px solid #e6ece9;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
  cursor: pointer;
  background: #fafcfa;
  transition: border-color 0.15s, background 0.15s;
}
.session-item:hover { border-color: #12664f; }
.session-item.current { border-color: #12664f; background: #f0f7f4; }
.queue-item { background: #fffaf2; border-color: #f0e0c4; }
.line1 { display: flex; align-items: center; justify-content: space-between; font-size: 13px; }
.line2 { margin-top: 6px; color: #6b7a74; font-size: 12px; }
.chat-panel { min-height: 420px; display: flex; gap: 14px; align-items: flex-start; }
/* 聊天区占满，右侧订单侧栏固定宽度 */
.chat-panel :deep(.el-tabs) { flex: 1; min-width: 0; }

/* ---------- 顾客历史订单侧栏 ---------- */
.cust-orders {
  width: 236px;
  flex: none;
  align-self: stretch;
  border-left: 1px solid #eef2f0;
  padding-left: 14px;
  display: flex;
  flex-direction: column;
  max-height: 460px;
}
.cust-orders__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.cust-orders__head b { font-size: 13px; color: #33463f; }
.cust-orders__cid { font-size: 11px; color: #8a94a6; }
.cust-orders__hint {
  font-size: 12px;
  color: #8a94a6;
  padding: 18px 0;
  text-align: center;
}
.cust-orders__list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 2px;
}
.cust-order {
  border: 1px solid #e6ece9;
  border-radius: 6px;
  padding: 8px 9px;
  cursor: pointer;
  background: #fafcfa;
  transition: border-color .15s, background .15s;
}
.cust-order:hover { border-color: #12664f; background: #f0f7f4; }
.cust-order--active { border-color: #12664f; background: #f0f7f4; }
.cust-order__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.cust-order__no {
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: #4a5b54;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cust-order__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
}
.cust-order__time { font-size: 11px; color: #8a94a6; }
.cust-order__amount { font-size: 12px; color: #d46b08; }
.cust-orders__tip {
  margin: 8px 0 0;
  font-size: 11px;
  color: #a6b0ac;
  text-align: center;
}
.tab-label { display: inline-flex; align-items: center; gap: 6px; }
.tab-body { padding-top: 4px; }
.message-box {
  height: 320px;
  overflow-y: auto;
  border: 1px solid #e6ece9;
  border-radius: 6px;
  background: #fafcfa;
  padding: 12px;
}
.message-line { display: flex; margin-bottom: 10px; }
.message-line.mine { justify-content: flex-end; }
.bubble {
  max-width: 72%;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e1e9e5;
  font-size: 13px;
  word-break: break-all;
}
.message-line.mine .bubble { background: #12664f; color: #fff; border-color: #12664f; }
.bubble small { opacity: 0.7; }
/* 图片气泡：去掉文字内边距，图片自己撑开；点开看大图 */
.bubble--img { padding: 4px; line-height: 0; }
.bubble-img { max-width: 200px; max-height: 200px; border-radius: 6px; cursor: zoom-in; display: block; }
.hidden-input { display: none; }
.send-row { display: flex; gap: 8px; margin-top: 12px; }
.tab-actions { margin-top: 12px; align-items: center; }
/* 顶栏右侧：今日完成接待数 */
.today-stat {
  margin-left: auto;
  display: flex;
  align-items: baseline;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 18px;
  background: #f0f7f4;
  border: 1px solid #d8e8e1;
}
.today-stat__label { font-size: 12px; color: #5c6b65; }
.today-stat__num { font-size: 18px; font-weight: 700; color: #12664f; line-height: 1; }
.today-stat__unit { font-size: 12px; color: #5c6b65; }

/* 客户正在输入 */
.typing-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  color: #8a94a6;
}
.typing-dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: #12664f;
  animation: typing-bounce 1.1s infinite ease-in-out;
}
.typing-dot:nth-child(2) { animation-delay: .15s; }
.typing-dot:nth-child(3) { animation-delay: .3s; }
@keyframes typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: .4; }
  30% { transform: translateY(-3px); opacity: 1; }
}
</style>
