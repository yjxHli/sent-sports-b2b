<template>
  <div>
    <h2 class="sent-page-title">客服指标</h2>
    <p class="sent-hint audit-intro">
      按客服汇总接待量、满意度与解决率。支持今天 / 近 7 天 / 近 30 天快捷查询，也可由经理自定义时间区间。
      口径：接待量按会话结束时间统计，满意率 = 满意评价 ÷ 有效评价，解决率 = 已解决会话 ÷ 接待量。
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
        <el-button type="primary" data-testid="metrics-query" @click="load">查询</el-button>
        <el-button data-testid="metrics-reset" @click="reset">重置</el-button>
      </div>

      <div class="metrics-summary">
        <div class="metrics-card">
          <span>客服人数</span><b>{{ rows.length }}</b><small>人</small>
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
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        size="small"
        class="metrics-table"
        data-testid="metrics-table"
        row-key="staffId"
      >
        <el-table-column prop="staffId" label="客服 ID" width="100" align="center" />
        <el-table-column prop="staffName" label="客服姓名" min-width="120" />
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
      <p v-if="!loading && !rows.length" class="metrics-empty">该时间区间内没有客服数据</p>
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
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { chatApi, type ChatAuditStaffVO, type ChatMetricsRowVO } from '@/api';

type Preset = 'today' | '7d' | '30d' | 'custom';

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
const loading = ref(false);
const rangeText = ref('');

const summary = computed(() => {
  const list = rows.value;
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
  void load();
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
    }));
    rangeText.value = `${data?.from ?? dateRange.value[0]} ~ ${data?.to ?? dateRange.value[1]}`;
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
});
</script>

<style scoped>
.audit-intro { margin-bottom: 12px; }
.metrics-toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.metrics-range { font-size: 12px; color: #6b7a74; padding: 0 4px; }
.metrics-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}
.metrics-card {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 12px 16px;
  border: 1px solid #e6ece9;
  border-radius: 10px;
  background: #fbfcfc;
}
.metrics-card span { font-size: 12px; color: #6b7a74; }
.metrics-card b { margin-left: auto; font-size: 22px; color: #12664f; line-height: 1; }
.metrics-card small { font-size: 12px; color: #8a94a6; }
.metrics-table { width: 100%; }
.metrics-empty { padding: 28px 0; text-align: center; font-size: 13px; color: #8a94a6; }
.rate { font-weight: 600; }
.rate--good { color: #178a68; }
.rate--warn { color: #d46b08; }
.rate--bad { color: #e34d59; }
.rate--idle { color: #9aa5a0; }
@media (max-width: 900px) {
  .metrics-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
