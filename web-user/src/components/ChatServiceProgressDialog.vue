<template>
  <ChatModal
    :open="open"
    testid="chat-progress-dialog"
    panel-class="progress-dlg"
    width="580px"
    @close="close"
  >
    <header class="progress-dlg__head">
      <div class="progress-dlg__title">
        <b>服务进度</b>
        <span>本次咨询到哪一步了，以及过去的服务记录</span>
      </div>
      <button type="button" class="progress-dlg__close" title="关闭" @click="close">×</button>
    </header>

    <div class="progress-dlg__body">
      <!-- 当前进度：四步走完就是一次完整咨询 -->
      <section class="progress-dlg__card">
        <div class="progress-dlg__card-head">
          <b>本次服务进度</b>
          <span class="progress-dlg__badge" :class="`progress-dlg__badge--${statusKey}`">
            {{ statusText }}
          </span>
        </div>

        <ol class="progress-dlg__steps">
          <li
            v-for="(step, i) in STEPS"
            :key="step"
            class="progress-dlg__step"
            :class="{
              'is-done': i < stepIndex,
              'is-current': i === stepIndex,
            }"
          >
            <i class="progress-dlg__dot">{{ i < stepIndex ? '✓' : i + 1 }}</i>
            <span class="progress-dlg__step-text">{{ step }}</span>
          </li>
        </ol>

        <div class="progress-dlg__meta">
          <div class="progress-dlg__meta-row">
            <span>会话编号</span>
            <b>{{ current?.sessionId ? `#${Number(current.sessionId)}` : '尚未开始' }}</b>
          </div>
          <div class="progress-dlg__meta-row">
            <span>接待客服</span>
            <b>{{ current?.staffId ? `客服 #${Number(current.staffId)}` : (queued ? '等待接入' : '—') }}</b>
          </div>
          <div class="progress-dlg__meta-row">
            <span>发起时间</span>
            <b>{{ current?.createdAtMillis ? fmtDateTime(Number(current.createdAtMillis)) : '—' }}</b>
          </div>
          <div v-if="queued" class="progress-dlg__meta-row">
            <span>排队位置</span>
            <b class="progress-dlg__warn">前方还有 {{ queueAhead }} 人</b>
          </div>
        </div>
      </section>

      <!-- 历史服务进度：最近几次咨询的结局 -->
      <section class="progress-dlg__card">
        <div class="progress-dlg__card-head">
          <b>历史服务进度</b>
          <span class="progress-dlg__hint">最近 {{ historyRows.length }} 次</span>
        </div>

        <div v-if="loading" class="progress-dlg__state">加载中…</div>
        <div v-else-if="historyRows.length === 0" class="progress-dlg__state">还没有历史服务记录</div>
        <ul v-else class="progress-dlg__history">
          <li v-for="s in historyRows" :key="s.sessionId" class="progress-dlg__history-row">
            <span class="progress-dlg__history-no">#{{ Number(s.sessionId) }}</span>
            <span class="progress-dlg__history-time">{{ fmtDateTime(Number(s.lastActiveAtMillis ?? s.createdAtMillis)) }}</span>
            <i class="progress-dlg__tag" :class="`progress-dlg__tag--${historyKey(s.status)}`">
              {{ historyText(s.status) }}
            </i>
          </li>
        </ul>
      </section>
    </div>
  </ChatModal>
</template>

<script setup lang="ts">
/**
 * 服务进度弹窗（2026-11-24）。
 *
 * 「服务进度」= 本次咨询走到哪一步（提交 → 接入 → 沟通 → 结束）；
 * 「历史服务进度」= 过去几次咨询的最终状态。两块数据同源于会话摘要列表，
 * 当前会话按 id 摘出去单列，剩下的才进历史，避免同一条出现两次。
 */
import { computed } from 'vue';
import { fmtDateTime } from '@sent/shared';
import type { SessionSummaryVO } from '@/api/chat';
import ChatModal from './ChatModal.vue';

interface CurrentProgress {
  sessionId: number;
  status: string;
  staffId?: number | null;
  createdAtMillis: number;
}

const props = withDefaults(defineProps<{
  open: boolean;
  sessions: SessionSummaryVO[];
  loading?: boolean;
  current?: CurrentProgress | null;
  queued?: boolean;
  queueAhead?: number;
}>(), { loading: false, current: null, queued: false, queueAhead: 0 });

const emit = defineEmits<{ close: [] }>();

const STEPS = ['提交咨询', '客服接入', '沟通中', '会话结束'];

/** 0=刚提交 1=已接入 2=沟通中 3=已结束 */
const stepIndex = computed(() => {
  const status = props.current?.status ?? '';
  if (status === 'CLOSED') return 3;
  if (status === 'ACTIVE') return 2;
  if (status === 'WAITING') return 0;
  return props.current?.sessionId ? 1 : 0;
});

const statusKey = computed(() => {
  const status = props.current?.status ?? '';
  if (status === 'CLOSED') return 'closed';
  if (status === 'ACTIVE') return 'active';
  return 'waiting';
});

const statusText = computed(() => {
  const status = props.current?.status ?? '';
  if (status === 'CLOSED') return '已结束';
  if (status === 'ACTIVE') return props.queued ? '排队中' : '服务中';
  if (status === 'WAITING') return '排队中';
  return props.current?.sessionId ? '服务中' : '未开始';
});

/** 历史列表：把当前这次排除掉，最多 6 条 */
const historyRows = computed(() => {
  const cur = Number(props.current?.sessionId ?? 0);
  return props.sessions.filter((s) => Number(s.sessionId) !== cur).slice(0, 6);
});

function historyKey(status: string): string {
  if (status === 'ACTIVE') return 'active';
  if (status === 'WAITING') return 'waiting';
  return 'closed';
}

function historyText(status: string): string {
  if (status === 'ACTIVE') return '服务中';
  if (status === 'WAITING') return '排队中';
  return '已结束';
}

function close(): void {
  emit('close');
}
</script>

<style scoped>
.progress-dlg__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 18px 12px;
  border-bottom: 1px solid #f2f4f5;
}
.progress-dlg__title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.progress-dlg__title b {
  font-size: 15px;
  color: var(--sent-text);
}
.progress-dlg__title span {
  font-size: 12px;
  color: var(--sent-muted);
}
.progress-dlg__close {
  margin-left: auto;
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  color: var(--sent-muted);
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 6px;
}
.progress-dlg__close:hover {
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
}

.progress-dlg__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 18px 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: #fbfcfc;
}
.progress-dlg__card {
  background: #fff;
  border: 1px solid #eef1f2;
  border-radius: 12px;
  padding: 14px 16px;
}
.progress-dlg__card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.progress-dlg__card-head b {
  font-size: 13px;
  color: var(--sent-text);
}
.progress-dlg__badge {
  font-size: 11px;
  line-height: 1;
  padding: 4px 8px;
  border-radius: 999px;
}
.progress-dlg__badge--active {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
}
.progress-dlg__badge--waiting {
  background: #fff7e6;
  color: #b8741a;
}
.progress-dlg__badge--closed {
  background: #f2f4f5;
  color: var(--sent-muted);
}
.progress-dlg__hint {
  margin-left: auto;
  font-size: 11px;
  color: var(--sent-muted);
}

/* 四步进度条：横排，靠伪元素连成一条线 */
.progress-dlg__steps {
  list-style: none;
  margin: 0 0 14px;
  padding: 0;
  display: flex;
  gap: 4px;
}
.progress-dlg__step {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  position: relative;
}
.progress-dlg__step::before {
  content: '';
  position: absolute;
  top: 10px;
  left: calc(-50% + 12px);
  width: calc(100% - 24px);
  height: 2px;
  background: #eceff1;
}
.progress-dlg__step:first-child::before {
  display: none;
}
.progress-dlg__step.is-done::before,
.progress-dlg__step.is-current::before {
  background: var(--sent-primary);
}
.progress-dlg__dot {
  position: relative;
  z-index: 1;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-style: normal;
  background: #f2f4f5;
  color: #9aa3af;
}
.progress-dlg__step.is-done .progress-dlg__dot {
  background: var(--sent-primary);
  color: #fff;
}
.progress-dlg__step.is-current .progress-dlg__dot {
  background: #fff;
  border: 2px solid var(--sent-primary);
  color: var(--sent-primary);
  font-weight: 600;
}
.progress-dlg__step-text {
  font-size: 11px;
  color: var(--sent-muted);
  text-align: center;
}
.progress-dlg__step.is-done .progress-dlg__step-text,
.progress-dlg__step.is-current .progress-dlg__step-text {
  color: var(--sent-primary);
}

.progress-dlg__meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  padding-top: 12px;
  border-top: 1px dashed #eef1f2;
}
.progress-dlg__meta-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  font-size: 12px;
}
.progress-dlg__meta-row span {
  color: var(--sent-muted);
  flex: none;
}
/* 一行两列时标签必须紧贴数值：两端对齐会让「会话编号  未开始  接待客服  —」串成一句 */
.progress-dlg__meta-row span::after {
  content: '：';
}
.progress-dlg__meta-row b {
  color: var(--sent-text);
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.progress-dlg__warn {
  color: #b8741a !important;
}

.progress-dlg__history {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.progress-dlg__history-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border: 1px solid #f1f3f4;
  border-radius: 9px;
  font-size: 12px;
}
.progress-dlg__history-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--sent-text);
}
.progress-dlg__history-time {
  flex: 1;
  color: var(--sent-muted);
}
.progress-dlg__tag {
  font-style: normal;
  font-size: 10px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 999px;
  flex: none;
}
.progress-dlg__tag--active {
  background: #e7f1ec;
  color: var(--sent-primary);
}
.progress-dlg__tag--waiting {
  background: #fff7e6;
  color: #b8741a;
}
.progress-dlg__tag--closed {
  background: #f2f4f5;
  color: var(--sent-muted);
}
.progress-dlg__state {
  padding: 18px 0;
  text-align: center;
  font-size: 12px;
  color: var(--sent-muted);
}

@media (max-width: 720px) {
  .progress-dlg__meta {
    grid-template-columns: 1fr;
  }
}
</style>
