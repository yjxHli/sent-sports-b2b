<template>
  <div>
    <h2 class="sent-page-title">客服工作台</h2>

    <div class="sent-panel">
      <div class="sent-form-row">
        <el-tag :type="connected ? 'success' : 'info'" effect="dark" size="small">
          {{ connected ? 'WebSocket 已连接' : 'WebSocket 未连接' }}
        </el-tag>
        <span class="sent-hint">在线客服 {{ onlineCount }}</span>
        <el-input-number v-model="staffId" :min="1" :controls="false" style="width: 140px" />
        <el-select v-model="presence" style="width: 130px" @change="changePresence">
          <el-option label="在线" :value="PresenceStatus.ONLINE" />
          <el-option label="忙碌" :value="PresenceStatus.BUSY" />
          <el-option label="离线" :value="PresenceStatus.OFFLINE" />
        </el-select>
        <el-button @click="connect">重连</el-button>
        <el-button @click="loadWorkload">刷新工作量</el-button>
        <span class="sent-hint" v-if="workload.length">
          接待量：{{ workload.map(w => `${w.staffName ?? w.staffId}=${w.activeSessions}`).join(' / ') }}
        </span>
      </div>
    </div>

    <div class="sent-panel">
      <h3 class="section-title">会话列表</h3>
      <SentTable
        :columns="columns"
        :rows="sessions"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="客服会话"
        :operation-width="110"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="loadSessions"
      >
        <template #toolbar>
          <el-select v-model="status" placeholder="会话状态" clearable style="width: 150px" @change="onSearch">
            <el-option
              v-for="s in Object.values(ChatSessionStatus)"
              :key="s"
              :label="statusLabel(s)"
              :value="s"
            />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </template>
        <template #col-status="{ row }"><StatusTag :status="row.status" /></template>
        <template #operations="{ row }">
          <el-button link type="primary" @click="selectSession(row)">接待</el-button>
        </template>
      </SentTable>
    </div>

    <el-row :gutter="16" class="workbench">
      <el-col :xs="24" :lg="16">
        <div class="sent-panel">
          <h3 class="section-title">
            消息区
            <span v-if="activeSession" class="sent-hint">会话 #{{ activeSession.sessionId }}</span>
          </h3>
          <el-empty v-if="!activeSession" description="请选择一笔会话" :image-size="70" />
          <template v-else>
            <div ref="messageBox" class="message-box">
              <div
                v-for="m in messages"
                :key="m.key"
                class="message-line"
                :class="{ mine: m.senderType === SenderType.STAFF }"
              >
                <span class="bubble">
                  {{ m.content }}
                  <small v-if="m.pending">· 发送中</small>
                </span>
              </div>
              <el-empty v-if="!messages.length" description="暂无消息" :image-size="50" />
            </div>
            <div class="send-row">
              <el-input
                v-model="draft"
                placeholder="输入消息后回车发送"
                @keyup.enter="sendMessage"
              />
              <el-button type="primary" :disabled="!connected" @click="sendMessage">发送</el-button>
            </div>
            <p class="sent-hint">
              消息带 clientMsgId 幂等键，断线重连后可用历史接口对齐；ACK 回执会自动消除「发送中」。
            </p>
          </template>
        </div>
      </el-col>

      <el-col :xs="24" :lg="8">
        <div class="sent-panel">
          <h3 class="section-title">会话操作</h3>
          <el-descriptions v-if="activeSession" :column="1" border size="small">
            <el-descriptions-item label="会话 ID">{{ activeSession.sessionId }}</el-descriptions-item>
            <el-descriptions-item label="状态"><StatusTag :status="activeSession.status" /></el-descriptions-item>
            <el-descriptions-item label="接待客服">{{ activeSession.staffId ?? '未分配' }}</el-descriptions-item>
            <el-descriptions-item label="客户 ID">{{ activeSession.customerId ?? '--' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="未选择会话" :image-size="60" />

          <el-form label-width="90px" class="action-form">
            <el-form-item label="转接给">
              <el-input-number v-model="transferForm.toStaffId" :min="1" :controls="false" style="width: 100%" />
            </el-form-item>
            <el-form-item label="原因">
              <el-input v-model="transferForm.reason" placeholder="转接 / 结束原因" />
            </el-form-item>
          </el-form>
          <div class="sent-form-row">
            <el-button type="primary" :disabled="!activeSession" @click="accept">接待</el-button>
            <el-button :disabled="!activeSession" @click="transfer">转接</el-button>
            <el-button type="danger" :disabled="!activeSession" @click="closeSession">结束</el-button>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
/**
 * 客服工作台（P6-42）：会话列表 + 实时消息 + 接待/转接/结束 + 在线状态。
 * WebSocket 细节全部收在 composables/useChatSocket，页面只负责状态渲染与业务动作。
 */
import { nextTick, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { chatApi, type WorkloadVO } from '@/api';
import { usePermissionStore } from '@/store/modules/permission';
import { ChatSessionStatus, PresenceStatus, SenderType, fmtDateTime, statusLabel } from '@sent/shared';
import { useChatSocket, type IncomingAck, type IncomingMessage } from '@/composables/useChatSocket';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

interface UiMessage {
  key: string;
  clientMsgId: string;
  senderType: string;
  content: string;
  createdAt?: number;
  pending?: boolean;
}

const columns: TableColumn[] = [
  { prop: 'sessionId', label: '会话 ID', width: 120 },
  { prop: 'status', label: '状态', width: 120, align: 'center' },
  { prop: 'staffId', label: '接待客服', width: 120, align: 'center' },
  { prop: 'customerId', label: '客户 ID', width: 130, align: 'center' },
  { prop: 'waitingSinceMillis', label: '等待开始', width: 170,
    formatter: (_r, v) => fmtDateTime(v) },
];

const permission = usePermissionStore();
const staffId = ref<number>(Number(permission.me?.userId ?? 1));
const presence = ref<string>(PresenceStatus.ONLINE);
const onlineCount = ref(0);
const workload = ref<WorkloadVO[]>([]);

const sessions = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const status = ref<string>('');
const activeSession = ref<any>(null);

const messages = ref<UiMessage[]>([]);
const draft = ref('');
const messageBox = ref<HTMLElement | null>(null);
const transferForm = reactive<{ toStaffId: number; reason: string }>({ toStaffId: 1, reason: '' });

function upsert(message: UiMessage): void {
  const index = messages.value.findIndex(
    (item) => !!message.clientMsgId && item.clientMsgId === message.clientMsgId,
  );
  if (index >= 0) {
    messages.value[index] = { ...messages.value[index], pending: false };
    return;
  }
  messages.value.push(message);
}

function scrollToBottom(): void {
  void nextTick(() => {
    if (messageBox.value) messageBox.value.scrollTop = messageBox.value.scrollHeight;
  });
}

function onFrame(frame: IncomingMessage): void {
  if (activeSession.value && frame.sessionId !== Number(activeSession.value.sessionId)) return;
  upsert({
    key: frame.clientMsgId || `seq_${frame.seq}`,
    clientMsgId: frame.clientMsgId,
    senderType: frame.senderType,
    content: frame.content,
    pending: false,
  });
  scrollToBottom();
}

function onAck(ack: IncomingAck): void {
  if (ack.event === 'REJECTED' || ack.code) {
    ElMessage.error(`消息被拒绝：${ack.reason ?? `code ${ack.code}`}`);
    return;
  }
  if (ack.clientMsgId) {
    const index = messages.value.findIndex((item) => item.clientMsgId === ack.clientMsgId);
    if (index >= 0) messages.value[index] = { ...messages.value[index], pending: false };
  }
}

const socket = useChatSocket({
  onMessage: onFrame,
  onAck,
  onConnected: (count) => { onlineCount.value = count; },
});
const { connected, connect, send } = socket;

async function loadSessions(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(chatApi.sessions({
      page: page.value, size: size.value, status: status.value || undefined,
    }));
    sessions.value = data?.list ?? [];
    total.value = data?.total ?? 0;
  } catch (error) {
    sessions.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '会话列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadWorkload(): Promise<void> {
  try {
    workload.value = await unwrap(chatApi.workload());
  } catch {
    workload.value = [];
  }
}

async function changePresence(): Promise<void> {
  try {
    await unwrap(chatApi.presence(Number(staffId.value), presence.value));
    ElMessage.success(`在线状态已设为 ${statusLabel(presence.value)}`);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '状态更新失败');
  }
}

async function selectSession(row: any): Promise<void> {
  activeSession.value = row;
  messages.value = [];
  try {
    const history = await unwrap(chatApi.history(Number(row.sessionId)));
    for (const item of history ?? []) {
      upsert({
        key: String(item.messageId),
        clientMsgId: item.clientMsgId ?? '',
        senderType: item.senderType,
        content: item.content,
        createdAt: item.createdAt,
      });
    }
    scrollToBottom();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '历史消息加载失败');
  }
}

function sendMessage(): void {
  const content = draft.value.trim();
  if (!content || !activeSession.value) return;
  const clientMsgId = send(Number(activeSession.value.sessionId), content);
  if (!clientMsgId) {
    ElMessage.warning('WebSocket 未连接，请稍候或点击重连');
    return;
  }
  upsert({
    key: clientMsgId,
    clientMsgId,
    senderType: SenderType.STAFF,
    content,
    pending: true,
  });
  draft.value = '';
  scrollToBottom();
}

/**
 * 列表刷新后把 activeSession 重新指向新行对象。
 * loadSessions() 只替换 sessions.value，activeSession 仍持有操作前的旧行，
 * 右侧「会话操作」面板就会一直显示旧的 状态/接待客服（例如接待后仍显示「待接入 / 未分配」）。
 */
function rebindActiveSession(): void {
  const current = activeSession.value;
  if (!current) return;
  const fresh = (sessions.value as any[]).find(
    (row) => Number(row.sessionId) === Number(current.sessionId),
  );
  // 当前页可能已不含该会话（如筛选了状态），此时保留旧对象避免面板突然清空。
  if (fresh) activeSession.value = fresh;
}

async function accept(): Promise<void> {
  if (!activeSession.value) return;
  try {
    await unwrap(chatApi.accept(Number(activeSession.value.sessionId), Number(staffId.value)));
    ElMessage.success('已接待该会话');
    await loadSessions();
    rebindActiveSession();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '接待失败');
  }
}

async function transfer(): Promise<void> {
  if (!activeSession.value) return;
  if (!transferForm.reason.trim()) {
    ElMessage.warning('请填写转接原因');
    return;
  }
  try {
    await unwrap(chatApi.transfer(
      Number(activeSession.value.sessionId),
      Number(transferForm.toStaffId),
      transferForm.reason.trim(),
    ));
    ElMessage.success('已转接');
    await loadSessions();
    rebindActiveSession();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '转接失败');
  }
}

async function closeSession(): Promise<void> {
  if (!activeSession.value) return;
  if (!transferForm.reason.trim()) {
    ElMessage.warning('请填写结束原因');
    return;
  }
  try {
    await unwrap(chatApi.close(Number(activeSession.value.sessionId), transferForm.reason.trim()));
    ElMessage.success('会话已结束');
    activeSession.value = null;
    messages.value = [];
    await loadSessions();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '结束会话失败');
  }
}

function onSearch(): void { page.value = 1; void loadSessions(); }
function onPage(value: number): void { page.value = value; void loadSessions(); }
function onSize(value: number): void { size.value = value; page.value = 1; void loadSessions(); }

onMounted(async () => {
  connect();
  await Promise.all([loadSessions(), loadWorkload()]);
});
</script>

<style scoped>
.section-title { margin: 0 0 14px; font-size: 15px; color: #33463f; }
.workbench { margin-top: 16px; }
.message-box {
  height: 300px;
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
.send-row { display: flex; gap: 8px; margin-top: 12px; }
.action-form { margin-top: 16px; }
</style>
