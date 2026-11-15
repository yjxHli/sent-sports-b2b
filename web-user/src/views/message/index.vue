<template>
  <div class="container message-page">
    <!-- 头部：类型筛选 + 未读概览 + 全部已读 -->
    <div class="msg-head">
      <div class="msg-tabs">
        <button
          v-for="t in TABS"
          :key="t.key || 'all'"
          type="button"
          class="msg-tab"
          :class="{ 'msg-tab--active': activeType === t.key }"
          @click="switchType(t.key)"
        >
          {{ t.label }}
        </button>
      </div>
      <div class="msg-head__right">
        <span class="msg-head__unread">未读 {{ msg.unreadCount }} 条</span>
        <el-button size="small" :disabled="msg.unreadCount === 0" @click="readAll">全部已读</el-button>
        <el-button size="small" type="danger" plain :disabled="total === 0" @click="clearAll">
          清空
        </el-button>
      </div>
    </div>

    <div v-if="loading" class="list-hint">加载中…</div>
    <el-empty v-else-if="messages.length === 0" description="暂无消息" />

    <div v-else class="msg-list">
      <div
        v-for="m in messages"
        :key="m.id"
        class="msg-item"
        :class="{ 'msg-item--unread': !m.read }"
        @click="openMessage(m)"
      >
        <span class="msg-item__dot" aria-hidden="true"></span>
        <div class="msg-item__body">
          <div class="msg-item__line">
            <span class="msg-item__type" :class="`msg-item__type--${m.msgType.toLowerCase()}`">
              {{ typeLabel(m.msgType) }}
            </span>
            <span class="msg-item__title">{{ m.title }}</span>
          </div>
          <p class="msg-item__content">{{ m.content }}</p>
        </div>
        <div class="msg-item__right">
          <span class="msg-item__time">{{ fmtDateTime(m.createdAt) }}</span>
          <a
            v-if="m.bizNo"
            class="msg-item__go"
            @click.stop="goBiz(m)"
          >
            查看详情 ›
          </a>
          <button
            type="button"
            class="msg-item__del"
            title="删除这条消息"
            @click.stop="removeOne(m)"
          >
            删除
          </button>
        </div>
      </div>
    </div>

    <el-pagination
      v-if="total > size"
      class="list-page"
      background
      layout="prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page"
      @current-change="onPage"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 消息中心（站内信）。
 *
 * 与「在线客服」(`/chat`) 是两套东西：客服是实时会话，站内信是异步通知，
 * 可稍后查看、可标记已读、可按类型筛选。
 */
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { messageApi, type MessageVO } from '@/api/message';
import { useMessageStore } from '@/store/modules/useMessageStore';

const router = useRouter();
/**
 * 未读数是「顶栏红点」与「本页头部」共用的同一份状态。
 * 放本地 ref 的话，本页点「全部已读」后顶栏红点要等用户切走页面才消失。
 */
const msg = useMessageStore();

const TABS = [
  { key: '', label: '全部' },
  { key: 'ORDER', label: '订单' },
  { key: 'PROMO', label: '优惠' },
  { key: 'SYSTEM', label: '系统' },
];

const TYPE_LABEL: Record<string, string> = {
  ORDER: '订单',
  PROMO: '优惠',
  SYSTEM: '系统',
};

const messages = ref<MessageVO[]>([]);
const loading = ref(false);
const activeType = ref('');
const page = ref(1);
const size = ref(10);
const total = ref(0);

function typeLabel(type: string): string {
  return TYPE_LABEL[type] ?? '通知';
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const res = await unwrap(messageApi.list({
      page: page.value,
      size: size.value,
      type: activeType.value || null,
    }));
    messages.value = (res.list ?? []).map((m) => ({ ...m, id: Number(m.id), read: Boolean(m.read) }));
    total.value = Number(res.total ?? 0);
  } finally {
    loading.value = false;
  }
}

function switchType(key: string): void {
  if (activeType.value === key) return;
  activeType.value = key;
  page.value = 1;
  void load();
}

async function onPage(p: number): Promise<void> {
  page.value = p;
  await load();
}

/** 点整行 = 标记已读（不跳走，避免误触离开消息中心）。标记后顶栏红点同步减一。 */
async function openMessage(m: MessageVO): Promise<void> {
  if (m.read) return;
  const id = Number(m.id);
  try {
    await unwrap(messageApi.markRead(id));
    m.read = true;
    msg.decrease(1);
    // 以服务端为准再校准一次（多处重复点击 / 别的端也读过时，本地减一可能与真实未读不一致）
    await msg.refresh();
  } catch {
    ElMessage.error('标记已读失败');
  }
}

async function readAll(): Promise<void> {
  try {
    await unwrap(messageApi.markAllRead());
    messages.value.forEach((m) => { m.read = true; });
    msg.reset();
    ElMessage.success('已全部标为已读');
    await msg.refresh();
  } catch {
    ElMessage.error('操作失败');
  }
}

/**
 * 删除单条。
 * 删掉当页最后一条时回退一页：否则会停在空页，用户看着像「消息全没了」。
 */
async function removeOne(m: MessageVO): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确定删除这条消息？', '删除消息', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await unwrap(messageApi.remove(Number(m.id)));
    ElMessage.success('已删除');
    if (messages.value.length === 1 && page.value > 1) page.value -= 1;
    await Promise.all([load(), msg.refresh()]);
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败');
  }
}

/** 一键清空：把当前账号的全部站内信删掉（含未读），二次确认后执行 */
async function clearAll(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `将清空全部 ${total.value} 条站内信（含未读），删除后不可恢复。`,
      '清空站内信',
      { confirmButtonText: '全部清空', cancelButtonText: '取消', type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    const deleted = await unwrap(messageApi.clearAll());
    ElMessage.success(`已清空 ${Number(deleted ?? 0)} 条站内信`);
    page.value = 1;
    await Promise.all([load(), msg.refresh()]);
  } catch (e) {
    ElMessage.error((e as Error).message || '清空失败');
  }
}

function goBiz(m: MessageVO): void {
  if (m.bizType === 'ORDER' && m.bizNo) {
    router.push(`/order/${m.bizNo}`);
    return;
  }
  if (m.bizType === 'CUSTOM') {
    router.push('/custom');
    return;
  }
  if (m.bizType === 'COUPON') {
    router.push('/member');
  }
}

onMounted(async () => {
  await Promise.all([load(), msg.refresh()]);
});
</script>

<style scoped>
.message-page {
  padding-top: 14px;
}

/* ---------- 头部 ---------- */
.msg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 8px 14px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.msg-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
}
.msg-tab {
  border: none;
  background: transparent;
  color: var(--sent-text-2);
  font-size: 15px;
  padding: 9px 18px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.msg-tab:hover {
  color: var(--sent-primary);
}
.msg-tab--active {
  color: var(--sent-primary);
  font-weight: 700;
  background: var(--sent-primary-soft);
}
.msg-head__right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.msg-head__unread {
  font-size: 13px;
  color: var(--sent-accent-dark);
}

/* ---------- 列表 ---------- */
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.msg-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 14px 16px;
  cursor: pointer;
  border: 1px solid transparent;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.msg-item:hover {
  border-color: var(--sent-primary);
  box-shadow: 0 6px 16px rgba(18, 102, 79, 0.1);
}
.msg-item__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 7px;
  flex: none;
  background: transparent;
}
.msg-item--unread .msg-item__dot {
  background: var(--sent-accent);
}
.msg-item__body {
  flex: 1;
  min-width: 0;
}
.msg-item__line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.msg-item__type {
  flex: none;
  font-size: 11px;
  border-radius: 4px;
  padding: 2px 6px;
  background: #f2f4f6;
  color: var(--sent-text-2);
}
.msg-item__type--order {
  background: #e8f3ef;
  color: var(--sent-primary);
}
.msg-item__type--promo {
  background: #fff1e6;
  color: var(--sent-accent-dark);
}
.msg-item__type--system {
  background: #eef2f8;
  color: #3f6fa8;
}
.msg-item__title {
  font-size: 14px;
  color: var(--sent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msg-item--unread .msg-item__title {
  font-weight: 700;
}
.msg-item__content {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--sent-muted);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.msg-item__right {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  min-width: 110px;
}
.msg-item__time {
  font-size: 12px;
  color: var(--sent-muted);
}
.msg-item__go {
  font-size: 12px;
  color: var(--sent-primary);
}
.msg-item__go:hover {
  color: var(--sent-accent);
}
/* 删除：默认淡灰，hover 变红，避免误触 */
.msg-item__del {
  border: none;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: 12px;
  color: var(--sent-muted);
  cursor: pointer;
  opacity: 0.75;
}
.msg-item__del:hover {
  color: #cf1322;
  opacity: 1;
}

.list-hint {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.list-page {
  justify-content: center;
  padding: 22px 0 8px;
}
</style>
