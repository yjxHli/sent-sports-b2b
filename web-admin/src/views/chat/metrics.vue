<template>
  <div>
    <h2 class="sent-page-title">客服指标</h2>
    <p class="sent-hint audit-intro">
      按客服汇总接待量、满意度与解决率。支持今天 / 近 7 天 / 近 30 天快捷查询，也可由经理自定义时间区间。
      口径：接待量按会话结束时间统计，满意率 = 满意评价 ÷ 有效评价，解决率 = 已解决会话 ÷ 接待量。
      「状态」列是客服此刻的在线状态（在线 / 忙碌 / 离线，10 秒自动刷新），可按状态筛选。
    </p>

    <div class="sent-panel metrics-panel">
      <div class="metrics-toolbar">
        <el-radio-group v-model="preset" data-testid="metrics-preset" @change="onPreset">
          <el-radio-button label="today">今天</el-radio-button>
          <el-radio-button label="7d">近 7 天</el-radio-button>
          <el-radio-button label="30d">近 30 天</el-radio-button>
          <el-radio-button label="custom">自定义</el-radio-button>
        </el-radio-group>

        <el-date-picker
          v-if="preset === 'custom'"
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
          data-testid="metrics-range"
        />
        <span v-else class="metrics-range" data-testid="metrics-range-text">{{ rangeText }}</span>

        <el-select v-model="staffId" clearable placeholder="全部客服" style="width: 150px"
                   data-testid="metrics-staff">
          <el-option v-for="s in staffOptions" :key="s.staffId" :label="`${s.staffId} ${s.name}`"
                     :value="s.staffId" />
        </el-select>
        <!-- 当前状态筛选：选项带实时人数，一眼能看出「现在有几个人在接单」 -->
        <el-select v-model="presenceFilter" clearable placeholder="全部状态" style="width: 130px"
                   data-testid="metrics-presence-filter">
          <el-option v-for="option in PRESENCE_OPTIONS" :key="option.value"
                     :label="`${option.label}（${presenceCounts[option.value] ?? 0}）`"
                     :value="option.value" />
        </el-select>
        <el-button type="primary" data-testid="metrics-query" @click="load">查询</el-button>
        <el-button data-testid="metrics-reset" @click="reset">重置</el-button>
      </div>

      <div class="metrics-summary">
        <div class="metrics-card">
          <span>客服人数</span><b>{{ visibleRows.length }}</b><small>人</small>
        </div>
        <div class="metrics-card">
          <span>总接待量</span><b>{{ summary.closed }}</b><small>次</small>
        </div>
        <div class="metrics-card">
          <span>平均满意率</span><b>{{ summary.satisfaction }}</b><small>{{ summary.satisfaction === '--' ? '' : '%' }}</small>
        </div>
        <div class="metrics-card">
          <span>平均解决率</span><b>{{ summary.resolution }}</b><small>{{ summary.resolution === '--' ? '' : '%' }}</small>
        </div>
        <!-- 状态汇总：指标页的「现在」视图，10 秒刷新一次 -->
        <div class="metrics-card metrics-card--presence" data-testid="metrics-presence-summary">
          <span>当前状态</span>
          <b class="presence-mini">
            <em class="presence-mini__item presence-mini__item--online">在线 {{ presenceCounts.ONLINE ?? 0 }}</em>
            <em class="presence-mini__item presence-mini__item--busy">忙碌 {{ presenceCounts.BUSY ?? 0 }}</em>
            <em class="presence-mini__item presence-mini__item--offline">离线 {{ presenceCounts.OFFLINE ?? 0 }}</em>
          </b>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="visibleRows"
        border
        size="small"
        class="metrics-table"
        data-testid="metrics-table"
        row-key="staffId"
      >
        <el-table-column prop="staffId" label="客服 ID" width="100" align="center" />
        <el-table-column prop="staffName" label="客服姓名" min-width="120" />
        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <span class="presence" :class="`presence--${String(row.presence).toLowerCase()}`"
                  data-testid="metrics-presence">
              <i class="presence__dot" />{{ presenceLabel(row.presence) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="closedCount" label="接待量" width="100" align="center" />
        <el-table-column prop="ratedCount" label="有效评价" width="100" align="center" />
        <el-table-column prop="satisfiedCount" label="满意数" width="100" align="center" />
        <el-table-column label="满意率" width="110" align="center">
          <template #default="{ row }">
            <span class="rate" :class="rateClass(row.satisfactionRate)" data-testid="metrics-satisfaction">
              {{ rateText(row.satisfactionRate) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="resolvedCount" label="已解决" width="100" align="center" />
        <el-table-column label="解决率" width="110" align="center">
          <template #default="{ row }">
            <span class="rate" :class="rateClass(row.resolutionRate)" data-testid="metrics-resolution">
              {{ rateText(row.resolutionRate) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="平均会话时长" min-width="130" align="center">
          <template #default="{ row }">{{ durationText(row.avgDurationSeconds) }}</template>
        </el-table-column>
      </el-table>
      <p v-if="!loading && !visibleRows.length" class="metrics-empty">
        {{ presenceFilter ? '没有处于该状态的客服' : '该时间区间内没有客服数据' }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 客服指标（2026-11-18 用户要求）：客服经理按天 / 近 7 天 / 近 30 天 / 自定义区间，
 * 查看每位客服的接待量、满意率、解决率与平均会话时长。
 * 聚合由后端 /admin/chat/metrics 完成，前端只做区间切换与展示；
 * JacksonConfig 把 Long 序列化成字符串，所有计数字段必须 Number() 归一。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { chatApi, type ChatAuditStaffVO, type ChatMetricsRowVO } from '@/api';

type Preset = 'today' | '7d' | '30d' | 'custom';
type Presence = 'ONLINE' | 'BUSY' | 'OFFLINE';

/** 与工作台/后端 PresenceStatus 一致的三态 */
const PRESENCE_OPTIONS: Array<{ value: Presence; label: string }> = [
  { value: 'ONLINE', label: '在线' },
  { value: 'BUSY', label: '忙碌' },
  { value: 'OFFLINE', label: '离线' },
];

const PRESENCE_LABELS: Record<string, string> = { ONLINE: '在线', BUSY: '忙碌', OFFLINE: '离线' };

function presenceLabel(value?: string | null): string {
  return PRESENCE_LABELS[String(value ?? '')] ?? '离线';
}

/** 状态刷新间隔：指标是历史数据，但「谁在线」必须是此刻的，10 秒够用又不打接口 */
const PRESENCE_REFRESH_MS = 10_000;

function dayText(offset: number): string {
  const d = new Date();
  d.setDate(d.getDate() - offset);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}

const preset = ref<Preset>('7d');
const dateRange = ref<string[]>([dayText(6), dayText(0)]);
const staffId = ref<number | undefined>(undefined);
const staffOptions = ref<ChatAuditStaffVO[]>([]);
const rows = ref<ChatMetricsRowVO[]>([]);
/** 顶部状态筛选：空 = 全部 */
const presenceFilter = ref<Presence | undefined>(undefined);
/** 实时状态（staffId → ONLINE/BUSY/OFFLINE），由 /admin/chat/workload 定时刷新 */
const presenceMap = ref<Record<number, string>>({});
const loading = ref(false);
const rangeText = ref('');
let presenceTimer: ReturnType<typeof setInterval> | undefined;

/** 行数据叠加实时状态：查询结果里的 presence 是兜底，轮询拿到后以轮询为准 */
const rowsWithPresence = computed<ChatMetricsRowVO[]>(() =>
  rows.value.map((r) => ({
    ...r,
    presence: presenceMap.value[r.staffId] ?? r.presence ?? 'OFFLINE',
  })),
);

const visibleRows = computed(() =>
  presenceFilter.value
    ? rowsWithPresence.value.filter((r) => r.presence === presenceFilter.value)
    : rowsWithPresence.value,
);

/** 各状态人数（筛选下拉里直接显示，省得再点一次才知道有没有人在线） */
const presenceCounts = computed<Record<string, number>>(() => {
  const counts: Record<string, number> = { ONLINE: 0, BUSY: 0, OFFLINE: 0 };
  for (const row of rowsWithPresence.value) {
    const key = String(row.presence ?? 'OFFLINE');
    counts[key] = (counts[key] ?? 0) + 1;
  }
  return counts;
});

const summary = computed(() => {
  // 汇总跟随筛选结果：筛选「在线」时应只看在线客服的接待量与解决率
  const list = visibleRows.value;
  const closed = list.reduce((s, r) => s + Number(r.closedCount ?? 0), 0);
  const rated = list.reduce((s, r) => s + Number(r.ratedCount ?? 0), 0);
  const satisfied = list.reduce((s, r) => s + Number(r.satisfiedCount ?? 0), 0);
  const resolved = list.reduce((s, r) => s + Number(r.resolvedCount ?? 0), 0);
  return {
    closed,
    satisfaction: rated > 0 ? String(Math.round(satisfied * 100 / rated)) : '--',
    resolution: closed > 0 ? String(Math.round(resolved * 100 / closed)) : '--',
  };
});

function rateText(value: number | string): string {
  const n = Number(value ?? -1);
  return n < 0 ? '--' : `${n}%`;
}

function rateClass(value: number | string): string {
  const n = Number(value ?? -1);
  if (n < 0) return 'rate--idle';
  if (n >= 90) return 'rate--good';
  if (n >= 70) return 'rate--warn';
  return 'rate--bad';
}

function durationText(seconds: number | string): string {
  const s = Math.max(0, Math.floor(Number(seconds ?? 0)));
  if (s < 60) return `${s} 秒`;
  const minutes = Math.floor(s / 60);
  const rest = s % 60;
  return rest ? `${minutes} 分 ${rest} 秒` : `${minutes} 分钟`;
}

function onPreset(value: Preset): void {
  if (value === 'today') dateRange.value = [dayText(0), dayText(0)];
  else if (value === '7d') dateRange.value = [dayText(6), dayText(0)];
  else if (value === '30d') dateRange.value = [dayText(29), dayText(0)];
  void load();
}

function reset(): void {
  preset.value = '7d';
  dateRange.value = [dayText(6), dayText(0)];
  staffId.value = undefined;
  presenceFilter.value = undefined;
  void load();
}

/**
 * 刷新坐席当前状态（不重新聚合指标）。
 *
 * <p>指标是区间历史值，坐席状态是此刻的值，两者更新频率不同：
 * 单独打一个轻量接口，不因为「有人切了忙碌」就重跑整张报表。</p>
 */
async function refreshPresence(): Promise<void> {
  try {
    const list = await unwrap(chatApi.workload());
    const map: Record<number, string> = {};
    for (const item of list ?? []) {
      map[Number(item.staffId)] = String(item.presence ?? 'OFFLINE');
    }
    presenceMap.value = map;
  } catch {
    /* 状态刷新失败保持上一轮，不影响指标主体 */
  }
}

async function load(): Promise<void> {
  if (!dateRange.value || dateRange.value.length !== 2) {
    ElMessage.warning('请选择统计时间区间');
    return;
  }
  loading.value = true;
  try {
    const data = await unwrap(chatApi.metrics({
      staffId: staffId.value,
      from: dateRange.value[0],
      to: dateRange.value[1],
    }));
    rows.value = (data?.rows ?? []).map((r) => ({
      ...r,
      staffId: Number(r.staffId),
      closedCount: Number(r.closedCount ?? 0),
      ratedCount: Number(r.ratedCount ?? 0),
      satisfiedCount: Number(r.satisfiedCount ?? 0),
      satisfactionRate: Number(r.satisfactionRate ?? -1),
      resolvedCount: Number(r.resolvedCount ?? 0),
      resolutionRate: Number(r.resolutionRate ?? -1),
      avgDurationSeconds: Number(r.avgDurationSeconds ?? 0),
      presence: String(r.presence ?? 'OFFLINE'),
    }));
    rangeText.value = `${data?.from ?? dateRange.value[0]} ~ ${data?.to ?? dateRange.value[1]}`;
    // 报表里带的 presence 是查询瞬间的快照，紧接着再拉一次实时状态，避免「刚打开就看到旧状态」
    await refreshPresence();
  } catch {
    rows.value = [];
    rangeText.value = '';
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  try {
    staffOptions.value = (await unwrap(chatApi.auditStaff())) ?? [];
  } catch {
    staffOptions.value = [];
  }
  await load();
  presenceTimer = setInterval(() => { void refreshPresence(); }, PRESENCE_REFRESH_MS);
});

onUnmounted(() => {
  if (presenceTimer) clearInterval(presenceTimer);
});
</script>

<style scoped>
.audit-intro { margin-bottom: 12px; }
.metrics-toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.metrics-range { font-size: 12px; color: #6b7a74; padding: 0 4px; }
.metrics-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 1px;
  margin: 16px 0;
  background: var(--sent-line);
  border: 1px solid var(--sent-line);
  border-radius: var(--r-panel);
  overflow: hidden;
}
.metrics-card {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 12px 16px;
  border: none;
  border-radius: 0;
  background: var(--sent-surface);
}
.metrics-card span { font-size: 12px; color: var(--sent-ink-3); }
.metrics-card b {
  margin-left: auto;
  font-size: 20px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: var(--sent-ink);
  line-height: 1;
}
.metrics-card small { font-size: 12px; color: var(--sent-ink-3); }
.metrics-table { width: 100%; }
.metrics-empty { padding: 28px 0; text-align: center; font-size: 13px; color: var(--sent-ink-3); }
.rate { font-weight: 500; font-variant-numeric: tabular-nums; }
.rate--good { color: var(--sent-ok); }
.rate--warn { color: var(--sent-warn); }
.rate--bad { color: var(--sent-danger); }
.rate--idle { color: var(--sent-ink-3); }

/* ---------- 实时状态 ---------- */
.presence { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 500; }
.presence__dot { width: 6px; height: 6px; border-radius: 50%; background: var(--sent-ink-3); display: inline-block; }
.presence--online { color: var(--sent-ok); }
.presence--online .presence__dot { background: var(--sent-ok); }
.presence--busy { color: var(--sent-warn); }
.presence--busy .presence__dot { background: var(--sent-warn); }
.presence--offline { color: var(--sent-ink-3); }
.presence--offline .presence__dot { background: var(--sent-ink-3); }

/* 状态汇总卡：三个小色块挤在一张卡里，字号比别的卡小一点 */
.metrics-card--presence b.presence-mini {
  margin-left: auto;
  display: inline-flex;
  gap: 10px;
  font-size: 12px;
  line-height: 1.2;
}
.presence-mini__item { font-style: normal; font-weight: 500; font-variant-numeric: tabular-nums; }
.presence-mini__item--online { color: var(--sent-ok); }
.presence-mini__item--busy { color: var(--sent-warn); }
.presence-mini__item--offline { color: var(--sent-ink-3); }

@media (max-width: 1100px) {
  .metrics-summary { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .metrics-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
