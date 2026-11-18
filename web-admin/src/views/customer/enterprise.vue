<template>
  <div>
    <h2 class="sent-page-title">企业审核与授信</h2>
    <el-row :gutter="16" class="enterprise-split">
      <el-col :xs="24" :lg="13">
        <div class="sent-panel split-panel">
          <h3 class="section-title">企业客户</h3>
          <div ref="leftTableRef" class="enterprise-table-wrap">
            <SentTable
              :columns="columns"
              :rows="rows"
              :total="total"
              :page="page"
              :size="size"
              :loading="loading"
              :page-sizes="tableSizes"
              exportable
              export-name="企业客户"
              :operation-width="110"
              @update:page="onPage"
              @update:size="onSize"
              @refresh="load"
            >
              <template #toolbar>
                <el-input
                  v-model="keyword"
                  placeholder="企业名称 / 企业 ID"
                  clearable
                  style="width: 200px"
                  @keyup.enter="onSearch"
                />
                <el-button type="primary" @click="onSearch">查询</el-button>
                <span class="sent-hint">后端精确筛选，共 {{ total }} 家</span>
              </template>
              <template #col-enterpriseStatus="{ row }">
                <StatusTag
                  :label="enterpriseStatusLabel(row.enterpriseStatus)"
                  :tone="enterpriseStatusTone(row.enterpriseStatus)"
                />
              </template>
              <template #col-creditState="{ row }">
                <StatusTag
                  :label="Number(row.creditLimitCent) > 0 ? '已授信' : '未授信'"
                  :tone="Number(row.creditLimitCent) > 0 ? 'success' : 'info'"
                />
              </template>
              <template #operations="{ row }">
                <el-button link type="primary" @click="select(row)">选择</el-button>
              </template>
            </SentTable>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :lg="11">
        <div class="sent-panel split-panel">
          <h3 class="section-title">审核与授信</h3>
          <el-alert
            v-if="!current"
            title="请从左侧选择一家企业客户"
            type="info"
            :closable="false"
            show-icon
          />
          <template v-else>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="企业客户">{{ current.name }}（{{ current.customerId }}）</el-descriptions-item>
              <el-descriptions-item label="认证状态">
                <StatusTag
                  :label="enterpriseStatusLabel(current.enterpriseStatus)"
                  :tone="enterpriseStatusTone(current.enterpriseStatus)"
                />
              </el-descriptions-item>
              <!-- 企业自助注册提交的材料：不展示就只能「盲审」 -->
              <el-descriptions-item label="企业名称">{{ current.companyName || '--' }}</el-descriptions-item>
              <el-descriptions-item label="统一社会信用代码">{{ current.licenseNo || '--' }}</el-descriptions-item>
              <el-descriptions-item label="营业执照">
                <el-image
                  v-if="current.licenseUrl"
                  :src="fileApi.url(current.licenseUrl)"
                  :preview-src-list="[fileApi.url(current.licenseUrl)]"
                  :preview-teleported="true"
                  fit="contain"
                  class="license-thumb"
                  data-testid="enterprise-license-img"
                />
                <span v-else class="sent-hint">未上传（历史档案或线下提交）</span>
              </el-descriptions-item>
              <el-descriptions-item label="当前授信额度">{{ fmtMoney(current.creditLimitCent) }}</el-descriptions-item>
              <el-descriptions-item label="已用额度">{{ fmtMoney(current.creditUsedCent) }}</el-descriptions-item>
              <el-descriptions-item label="可用额度">{{ fmtMoney(availableCent) }}</el-descriptions-item>
            </el-descriptions>

            <h4 class="sub-title">企业认证审核</h4>
            <div class="sent-form-row">
              <el-button type="success" :loading="auditing" @click="audit(true)">审核通过</el-button>
              <el-button type="danger" :loading="auditing" @click="audit(false)">审核驳回</el-button>
            </div>
            <p class="sent-hint">审核结论写入企业认证档案（通过 ACTIVE / 驳回 REJECTED），左侧「认证状态」即时刷新。</p>

            <h4 class="sub-title">企业授信（危险操作）</h4>
            <el-form label-width="110px">
              <el-form-item label="授信总额(元)">
                <el-input v-model="creditForm.totalYuan" placeholder="如 50000.00" clearable />
              </el-form-item>
              <el-form-item label="账期(天)">
                <el-input-number v-model="creditForm.accountPeriodDays" :min="1" :max="365" style="width: 100%" />
              </el-form-item>
              <el-form-item label="生效时间">
                <el-date-picker v-model="creditForm.validFrom" type="datetime" style="width: 100%" />
              </el-form-item>
              <el-form-item label="失效时间">
                <el-date-picker v-model="creditForm.validTo" type="datetime" style="width: 100%" />
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="creditForm.remark" type="textarea" :rows="2" />
              </el-form-item>
            </el-form>
            <el-button type="primary" :loading="submitting" @click="submitCredit">
              确认授信
            </el-button>
            <p class="sent-hint">提交前需按提示准确输入确认短语换取一次性令牌，令牌用后即焚。</p>
          </template>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
/**
 * 企业审核与授信（P6-40）：左侧企业客户、右侧审核 + 授信。
 * 授信是危险操作：金额由「元」经 yuanToCent 换算成「分」后，
 * 必须 openConfirm('ENTERPRISE_CREDIT') 换一次性令牌，且提交前复查过期。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { customerApi, financeApi } from '@/api';
import { fileApi } from '@/api/file';
import { fmtMoney, yuanToCent } from '@sent/shared';
import { openConfirm } from '@/utils/confirm';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'customerId', label: '客户 ID', width: 110 },
  { prop: 'name', label: '企业名称', minWidth: 160,
    // 自助注册的企业有公司全称就显示全称，历史演示档案退回昵称
    formatter: (r, v) => r.companyName || String(v ?? '') },
  { prop: 'enterpriseStatus', label: '认证状态', width: 100, align: 'center' },
  { prop: 'creditLimitCent', label: '授信额度', width: 120, align: 'right',
    formatter: (_r, v) => fmtMoney(v) },
  { prop: 'creditUsedCent', label: '已用额度', width: 120, align: 'right',
    formatter: (_r, v) => fmtMoney(v) },
  { prop: 'creditState', label: '授信状态', width: 100, align: 'center' },
];

/**
 * 认证状态标签：真库 enterprise_profile.status 里 ACTIVE 表示已认证、
 * REJECTED 表示已驳回，NORMAL / PENDING 视为待审核（演示数据用的是 NORMAL）。
 */
function enterpriseStatusLabel(status?: string): string {
  const key = (status ?? '').toUpperCase();
  if (key === 'ACTIVE') return '已认证';
  if (key === 'REJECTED') return '已驳回';
  return '待审核';
}

function enterpriseStatusTone(status?: string): 'success' | 'danger' | 'warning' {
  const key = (status ?? '').toUpperCase();
  if (key === 'ACTIVE') return 'success';
  if (key === 'REJECTED') return 'danger';
  return 'warning';
}

const { page, size } = usePagination();
const rows = ref<any[]>([]);
const total = ref(0);
const keyword = ref('');
const loading = ref(false);
const auditing = ref(false);
const submitting = ref(false);
const current = ref<any>(null);

/**
 * 宽屏（lg ≥ 1200px）左右卡片等高后，左侧列表会空出一截。
 * 这里按可用高度算出每页能放几行，让它正好填到分页器上方；
 * 用户手动选过条数后（autoFitSize=false）不再自作主张。
 */
const leftTableRef = ref<HTMLElement | null>(null);
const panelFill = ref(false);
const autoFitSize = ref(true);
let panelMq: MediaQueryList | null = null;
let fitObserver: ResizeObserver | null = null;
let fitTimer: number | null = null;
let fitArmed = false;
let applyingFit = false;

/** 自适应出来的条数要并进下拉，否则 el-pagination 显示空白 */
const tableSizes = computed(() => {
  const base = [10, 15, 20, 50, 100];
  const currentSize = size.value;
  return base.includes(currentSize)
    ? base
    : [...base, currentSize].sort((a, b) => a - b);
});

function computeFitSize(): number {
  const wrap = leftTableRef.value;
  if (!wrap) return size.value;
  const toolbar = wrap.querySelector('.sent-table-toolbar') as HTMLElement | null;
  const pager = wrap.querySelector('.sent-table-pager') as HTMLElement | null;
  const header = wrap.querySelector('.el-table__header-wrapper') as HTMLElement | null;
  const firstRow = wrap.querySelector('.el-table__body tbody tr') as HTMLElement | null;
  const rowHeight = firstRow?.offsetHeight || 48;
  // toolbar 自带 12px 下边距、分页器自带 14px 上边距；无分页器时按 54px 占位估算
  const fixed = (toolbar?.offsetHeight ?? 44) + 12
    + (pager ? pager.offsetHeight + 14 : 54)
    + (header?.offsetHeight ?? 40);
  const available = wrap.clientHeight - fixed;
  if (available <= 0 || rowHeight <= 0) return size.value;
  return Math.max(5, Math.floor(available / rowHeight));
}

function applyFitSize(): void {
  if (!panelFill.value || !autoFitSize.value) return;
  const fit = computeFitSize();
  if (fit === size.value) return;
  applyingFit = true;
  size.value = fit;
  page.value = 1;
  void nextTick(() => { applyingFit = false; });
  void load();
}

/** 首屏高度会变化几次（列表数据落地、卡片定型），防抖合并后再测量 */
function scheduleFit(): void {
  if (fitTimer !== null) window.clearTimeout(fitTimer);
  fitTimer = window.setTimeout(() => {
    fitTimer = null;
    applyFitSize();
  }, 300);
}

function armFit(): void {
  fitArmed = true;
  scheduleFit();
}

function syncPanelFill(): void {
  panelFill.value = panelMq?.matches ?? false;
}

/** 可用额度不在列表列里，由总额度 − 已用额度算，与后端「可用 = 总额 − 已用」口径一致。 */
const availableCent = computed(() => {
  const limit = Number(current.value?.creditLimitCent ?? 0);
  const used = Number(current.value?.creditUsedCent ?? 0);
  return Math.max(limit - used, 0);
});

const creditForm = reactive<{
  totalYuan: string;
  accountPeriodDays: number;
  validFrom: Date | null;
  validTo: Date | null;
  remark: string;
}>({
  totalYuan: '',
  accountPeriodDays: 30,
  validFrom: null,
  validTo: null,
  remark: '',
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(customerApi.list({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      type: 'ENTERPRISE',
    }));
    rows.value = data?.list ?? [];
    // Jackson 会把 Long 序列化成字符串，ElPagination 的 total 只收 Number
    total.value = Number(data?.total ?? 0);
    syncCurrent();
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '企业客户加载失败');
  } finally {
    // 列表落地后卡片高度才定型，这时再量每页能放几行
    loading.value = false;
    armFit();
  }
}

/**
 * 刷新后把右侧面板指回同一条记录：列表行是新建对象，
 * 不同步会让面板一直显示改动前的「未授信 / 未认证」旧值。
 */
function syncCurrent(): void {
  if (!current.value) return;
  const id = Number(current.value.customerId);
  const fresh = rows.value.find((r) => Number(r.customerId) === id);
  current.value = fresh ?? null;
}

function onSearch(): void {
  page.value = 1;
  void load();
}
function onPage(value: number): void {
  page.value = value;
  void load();
}
function onSize(value: number): void {
  // 用户主动选条数 → 关掉自适应
  if (!applyingFit) autoFitSize.value = false;
  size.value = value;
  page.value = 1;
  void load();
}

function select(row: any): void {
  current.value = row;
}

async function audit(approved: boolean): Promise<void> {
  if (!current.value) return;
  try {
    await ElMessageBox.confirm(
      `确认${approved ? '通过' : '驳回'}「${current.value.name}」的企业认证？`,
      '企业认证审核',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  auditing.value = true;
  try {
    const result = await unwrap(customerApi.auditEnterprise(
      Number(current.value.customerId), approved, approved ? '资料齐全' : '资料不完整',
    ));
    ElMessage.success(
      approved ? '审核已通过' : '已驳回',
    );
    // 后端回传落库后的状态，先本地回写再做一次真查，保证标签与库里一致
    if (current.value) current.value.enterpriseStatus = result?.status ?? current.value.enterpriseStatus;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '审核失败');
  } finally {
    auditing.value = false;
  }
}

async function submitCredit(): Promise<void> {
  if (!current.value) return;
  const totalCent = yuanToCent(creditForm.totalYuan);
  if (totalCent === null || totalCent <= 0) {
    ElMessage.warning('请填写有效的授信金额');
    return;
  }
  if (!creditForm.validFrom || !creditForm.validTo
      || creditForm.validTo.getTime() <= creditForm.validFrom.getTime()) {
    ElMessage.warning('失效时间必须晚于生效时间');
    return;
  }
  const customerId = Number(current.value.customerId);
  try {
    const grant = await openConfirm({
      action: 'ENTERPRISE_CREDIT',
      targetId: String(customerId),
      summary: `企业 ${current.value.name} · 授信 ¥${creditForm.totalYuan} · 账期 ${creditForm.accountPeriodDays} 天`,
    });
    if (grant.isExpired()) {
      ElMessage.warning('确认已超时，请重新确认');
      return;
    }
    submitting.value = true;
    await unwrap(financeApi.credit(customerId, {
      totalCent,
      accountPeriodDays: Number(creditForm.accountPeriodDays),
      validFromMillis: creditForm.validFrom.getTime(),
      validToMillis: creditForm.validTo.getTime(),
      remark: creditForm.remark || undefined,
    }, grant.token));
    ElMessage.success('授信成功');
    creditForm.totalYuan = '';
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '授信失败');
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  panelMq = window.matchMedia('(min-width: 1200px)');
  syncPanelFill();
  panelMq.addEventListener('change', onPanelFillChange);

  // 卡片高度定型后（load 的 finally 里）才首次测量；这里只挂监听
  void nextTick(() => {
    if (typeof ResizeObserver !== 'undefined') {
      fitObserver = new ResizeObserver(() => { if (fitArmed) scheduleFit(); });
      if (leftTableRef.value) fitObserver.observe(leftTableRef.value);
    }
  });

  void load();
});

/** 进出宽屏时重新量一次：窄屏两列堆叠，不参与自适应 */
function onPanelFillChange(): void {
  syncPanelFill();
  if (panelFill.value && fitArmed) void nextTick(() => scheduleFit());
}

onBeforeUnmount(() => {
  panelMq?.removeEventListener('change', onPanelFillChange);
  panelMq = null;
  fitObserver?.disconnect();
  fitObserver = null;
  if (fitTimer !== null) window.clearTimeout(fitTimer);
  fitTimer = null;
});
</script>

<style scoped>
.section-title { margin: 0 0 14px; font-size: 15px; color: #33463f; }
.sub-title { margin: 18px 0 10px; font-size: 14px; color: #33463f; }
/* 营业执照缩略图：点击可放大预览，审核时能看清企业名称与信用代码 */
.license-thumb {
  width: 96px;
  height: 64px;
  border: 1px solid #e6ece9;
  border-radius: 6px;
  background: #fafcfb;
  cursor: zoom-in;
}

/*
 * 双列等高（lg ≥1200px）：「企业客户」与「审核与授信」两张卡片底边对齐。
 * el-col 变 flex 容器 + 卡片 flex:1 撑满列高；左侧列表区 flex:1 吃满剩余高度，
 * 配合「每页条数按可用高度自适应」，表格正好填到分页器上方，不留大片空白。
 * 窄屏两列堆叠时不再强制等高，否则整卡会被拉出空白。
 */
@media (min-width: 1200px) {
  .enterprise-split { align-items: stretch; }
  .enterprise-split :deep(.el-col) { display: flex; }
  .enterprise-split .split-panel {
    flex: 1 1 auto;
    width: 100%;
    display: flex;
    flex-direction: column;
    /* 表格列宽总和可能大于列宽，flex item 默认按 min-content 撑开会把卡片顶出列外
       （实测两卡重叠 20px、中间缝隙消失），允许收缩回列宽 */
    min-width: 0;
    /* 与右侧「已选企业」时的实测高度对齐：未选时右侧只有一条提示，
       没有下限会让卡片矮一截，点「选择」后整页高度突跳、左侧条数也跟着重排 */
    min-height: 752px;
  }
  /* 标题等固定内容不压缩；列表区负责吃掉剩余高度 */
  .enterprise-split .split-panel > *:not(.enterprise-table-wrap) { flex-shrink: 0; }
  /* 纵向 flex 默认 align-items: stretch 会把「确认授信」按钮拉成整行，按原宽度左对齐 */
  .enterprise-split .split-panel > .el-button { align-self: flex-start; }
  .enterprise-split .enterprise-table-wrap {
    flex: 1 1 0;
    min-width: 0;
    min-height: 0;
  }
}
</style>
