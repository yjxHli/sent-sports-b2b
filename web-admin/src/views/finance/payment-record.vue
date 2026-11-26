<template>
  <div>
    <h2 class="sent-page-title">回款登记与核销</h2>
    <el-row :gutter="16" class="finance-split">
      <el-col :xs="24" :lg="10">
        <div class="sent-panel split-panel">
          <h3 class="section-title">登记回款</h3>
          <el-form label-width="110px">
            <el-form-item label="客户 ID">
              <el-input-number v-model="recordForm.customerId" :min="1" :controls="false" style="width: 100%" />
            </el-form-item>
            <el-form-item label="回款金额(元)">
              <el-input v-model="recordForm.amountYuan" placeholder="如 1200.00" clearable />
            </el-form-item>
            <el-form-item label="银行流水号">
              <el-input v-model="recordForm.serialNo" placeholder="必填" clearable />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="recordForm.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
          <div class="sent-form-row">
            <el-button type="primary" :loading="recording" @click="submitRecord">登记回款</el-button>
            <span class="sent-hint">幂等键 {{ idemKey || '提交时生成' }}</span>
          </div>
          <p class="sent-hint">金额由「元」换算为「分」提交；失败重试复用同一幂等键，成功后换键。</p>

          <h4 class="sub-title">回款记录</h4>
          <div class="sent-form-row">
            <el-button size="small" :loading="loadingRecords" @click="loadRecords()">刷新列表</el-button>
            <el-button size="small" :loading="exportingRecords" @click="exportRecords">导出 CSV</el-button>
            <el-checkbox v-model="onlyCurrentCustomer" @change="onFilterChange">仅看当前客户</el-checkbox>
            <BatchDeleteButton
              v-model:active="recordDeleteMode"
              :ids="selectedRecords.map((r: any) => String(r.paymentNo))"
              :summary="`已选择 ${selectedRecords.length} 笔回款`"
              :delete-fn="(ids, config) => unwrap(financeApi.batchDeletePaymentRecords(ids, config))"
              @done="onRecordsDeleted"
            />
            <span class="sent-hint">共 {{ totalRecords }} 笔回款，分页查看</span>
          </div>
          <el-table :data="records" border size="small" highlight-current-row @current-change="onSelectRecord"
                    @selection-change="selectedRecords = $event">
            <el-table-column v-if="recordDeleteMode" type="selection" width="44" />
            <el-table-column prop="paymentNo" label="回款单号" min-width="126" />
            <el-table-column label="金额" width="84" align="right">
              <template #default="scope">{{ fmtMoney(scope.row.amountCent) }}</template>
            </el-table-column>
            <el-table-column label="已核销" width="84" align="right">
              <template #default="scope">{{ fmtMoney(scope.row.writtenOffCent ?? 0) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="74" align="center">
              <template #default="scope"><StatusTag :status="scope.row.status" /></template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无回款记录" :image-size="50" />
            </template>
          </el-table>
          <div v-if="totalRecords > 0" class="record-pager">
            <el-pagination
              background
              small
              layout="total, sizes, prev, pager, next"
              :total="totalRecords"
              :current-page="page"
              :page-size="size"
              :page-sizes="[10, 15, 20, 50]"
              @current-change="onPageChange"
              @size-change="onSizeChange"
            />
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :lg="14">
        <div class="sent-panel split-panel">
          <h3 class="section-title">核销与冲销</h3>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="当前回款单">{{ activeRecord?.paymentNo ?? '请先登记或选择' }}</el-descriptions-item>
            <el-descriptions-item label="回款金额">{{ fmtMoney(activeRecord?.amountCent) }}</el-descriptions-item>
            <el-descriptions-item label="最大可核销">{{ fmtMoney(availableCent) }}</el-descriptions-item>
          </el-descriptions>
          <p v-if="activeRecord" class="sent-hint">
            已核销 {{ fmtMoney(activeRecord.writtenOffCent ?? 0) }}；
            最大可核销 = 回款金额 − 已核销，超出会直接提示具体差额。
          </p>

          <div class="sent-form-row section-tools">
            <el-button :disabled="!activeRecord" :loading="loadingReceivables" @click="reloadReceivables">
              加载该客户应收
            </el-button>
            <span class="sent-hint">
              共 {{ totalReceivables }} 笔未结清应收，分页查看；勾选应收行并填写本次核销金额（元）
            </span>
          </div>

          <div ref="receivableTableRef" class="receivable-table-wrap">
            <!-- 不给 height：条数已按可用高度算成整行，表格自然高度正好贴合最后一行，
                 强行撑满反而会多出一条带边框的空行 -->
            <el-table
              ref="receivableGridRef"
              :data="receivables"
              border
              size="small"
              row-key="receivableId"
              v-loading="loadingReceivables"
              @selection-change="selectedReceivables = $event"
            >
              <el-table-column type="selection" width="44" />
              <el-table-column prop="receivableId" label="应收 ID" width="84" />
              <el-table-column prop="orderNo" label="订单号" min-width="150" />
              <el-table-column label="未核销" width="96" align="right">
                <template #default="scope">
                  {{ fmtMoney(remainingCent(scope.row)) }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="74" align="center">
                <template #default="scope"><StatusTag :status="scope.row.status" /></template>
              </el-table-column>
              <el-table-column label="本次核销(元)" width="140">
                <template #default="scope">
                  <el-input v-model="writeOffAmounts[scope.row.receivableId]" size="small" placeholder="0.00" />
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无未结清应收" :image-size="50" />
              </template>
            </el-table>
          </div>

          <!-- 分页条始终占位：空数据时若 v-if 隐藏，表格可用高度会多出 30 多像素，
               自适应条数就会先算成一个偏大的值，加载后又缩回去 -->
          <div
            class="receivable-pager"
            :class="{ 'pager-placeholder': totalReceivables === 0 }"
          >
            <el-pagination
              background
              small
              layout="total, sizes, prev, pager, next"
              :total="totalReceivables"
              :current-page="receivablePage"
              :page-size="receivableSize"
              :page-sizes="receivableSizes"
              @current-change="onReceivablePage"
              @size-change="onReceivableSize"
            />
          </div>

          <div class="panel-footer">
            <div class="sent-form-row section-tools">
              <el-button type="primary" :disabled="!activeRecord" :loading="submitting" @click="writeOff">
                确认核销
              </el-button>
              <el-button type="danger" :disabled="!activeRecord" :loading="submitting" @click="reverse">
                回款冲销
              </el-button>
            </div>
            <p class="sent-hint">核销与冲销均为危险操作：需按提示输入确认短语换取一次性令牌，提交前复查过期。</p>
          </div>

        </div>
      </el-col>
    </el-row>

    <div class="sent-panel">
      <h3 class="section-title">核销与冲销明细</h3>
      <SentTable
        :select-all-rows="exportAllWriteOffs"
        :columns="writeOffColumns"
        :rows="writeOffRows"
        :total="writeOffTotal"
        :page="writeOffPage"
        :size="writeOffSize"
        :loading="loadingWriteOffs"
        :selectable="writeOffDeleteMode"
        row-key="id"
        exportable
        export-name="核销与冲销明细"
        :export-rows="exportAllWriteOffs"
        @selection-change="selectedWriteOffs = $event"
        @update:page="onWriteOffPage"
        @update:size="onWriteOffSize"
        @refresh="loadWriteOffs"
      >
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="writeOffDeleteMode"
            :ids="selectedWriteOffs.map((r: any) => String(r.id))"
            :summary="`已选择 ${selectedWriteOffs.length} 条核销明细`"
            :delete-fn="(ids, config) => unwrap(financeApi.batchDeleteWriteOffs(ids, config))"
            @done="onWriteOffsDeleted"
          />
        </template>
        <template #toolbar>
          <span class="sent-hint">
            {{ recordForm.customerId ? `仅看客户 ${recordForm.customerId} 的明细` : '全部客户的核销明细' }}
          </span>
        </template>
        <template #col-amountCent="{ row }">{{ fmtMoney(row.amountCent) }}</template>
        <template #col-createdAtMillis="{ row }">
          {{ row.createdAtMillis ? fmtDateTime(row.createdAtMillis) : '—' }}
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 回款登记与核销（P6-45）。
 * - 登记：元 → 分换算 + Idempotency-Key；成功后刷新真库回款列表。
 * - 列表：GET /admin/payment-records 真分页，按当前表单客户过滤，不再是「本次会话结果」。
 * - 核销：按应收行填写本次核销金额（分），分配仍由后端 WriteOffAllocator 决定，前端不越权重算。
 * - 核销/冲销：openConfirm('PAYMENT_WRITE_OFF' / 'PAYMENT_REVERSE') 换令牌 + isExpired() 复查。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { financeApi, type PaymentRecordVO, type WriteOffRow } from '@/api';
import { fmtDateTime, fmtMoney, yuanToCent } from '@sent/shared';
import { downloadCsv } from '@/utils/exportCsv';
import { openConfirm } from '@/utils/confirm';
import { useIdempotencyKey } from '@/utils/idempotent';
import { useTable } from '@/composables/useTable';
import StatusTag from '@/components/StatusTag.vue';
import SentTable from '@/components/SentTable.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import { useBatchDelete } from '@/composables/useBatchDelete';
import type { TableColumn } from '@/components/types';

const route = useRoute();
const idem = useIdempotencyKey('payment_record');
const idemKey = ref('');

const recordForm = reactive({
  customerId: Number(route.query.customerId ?? 1),
  amountYuan: '',
  serialNo: '',
  remark: '',
});
const { deleteMode: recordDeleteMode, selected: selectedRecords,
  onDeleted: onRecordsDeleted } = useBatchDelete(() => loadRecords());
const exportingRecords = ref(false);

async function exportRecords(): Promise<void> {
  exportingRecords.value = true;
  try {
    // 整表导出由门面用同一套筛选条件拉全，不再手抄一遍翻页循环
    const all = await fetchAllRecords();
    downloadCsv('回款记录', [
      { label: '回款单号', value: (r) => r.paymentNo },
      { label: '客户ID', value: (r) => r.customerId },
      { label: '金额(分)', value: (r) => r.amountCent },
      { label: '已核销(分)', value: (r) => r.writtenOffCent },
      { label: '流水号', value: (r) => r.serialNo },
      { label: '状态', value: (r) => r.status },
      { label: '备注', value: (r) => r.remark ?? '' },
    ], all);
  } finally {
    exportingRecords.value = false;
  }
}

const writeOffColumns: TableColumn[] = [
  { prop: 'id', label: '明细 ID', width: 100 },
  { prop: 'writeOffNo', label: '核销单号', minWidth: 160 },
  { prop: 'paymentRecordNo', label: '回款单号', minWidth: 160 },
  { prop: 'customerId', label: '客户 ID', width: 110 },
  { prop: 'receivableId', label: '应收 ID', width: 110 },
  { prop: 'amountCent', label: '核销金额', width: 130, align: 'right' },
  { prop: 'createdAtMillis', label: '核销时间', width: 170 },
];
/**
 * 核销明细表：同页第二张表，页码不落 sessionStorage（避免与回款主表抢同一个记忆键）。
 * 失败只展示空表，不弹错。
 */
const { rows: writeOffRows, total: writeOffTotal, loading: loadingWriteOffs,
  page: writeOffPage, size: writeOffSize, load: loadWriteOffs,
  onPage: onWriteOffPage, onSize: onWriteOffSize,
  fetchAll: exportAllWriteOffs } = useTable<WriteOffRow>({
  fetch: (params) => unwrap(financeApi.writeOffs({
    ...params,
    customerId: Number(recordForm.customerId) || undefined,
  })),
  defaultSize: 10,
  paginationKey: 'payment-record/write-offs',
  rememberPagination: false,
  errorMessage: '',
  immediate: false,
});
const { deleteMode: writeOffDeleteMode, selected: selectedWriteOffs,
  onDeleted: onWriteOffsDeleted } = useBatchDelete(loadWriteOffs);

const activeRecord = ref<PaymentRecordVO | null>(null);
const onlyCurrentCustomer = ref(false);
/** 登记成功后要「刷新后停在刚建的那一笔」，由 onLoaded 消费后清空 */
let preferPaymentNo: string | undefined;
/** 回款记录分页：真库 483 笔，必须翻页查看；页码/每页条数按路由记忆。 */
const { rows: records, total: totalRecords, loading: loadingRecords,
  page, size, load: loadRecords, onPage: onPageChange, onSize: onSizeChange,
  onSearch: onFilterChange, fetchAll: fetchAllRecords } = useTable<PaymentRecordVO>({
  fetch: (params) => unwrap(financeApi.paymentRecords({
    ...params,
    customerId: onlyCurrentCustomer.value ? Number(recordForm.customerId) || undefined : undefined,
  })),
  defaultSize: 10,
  errorMessage: '回款列表加载失败',
  onLoaded: (list) => {
    const target = preferPaymentNo ?? activeRecord.value?.paymentNo;
    preferPaymentNo = undefined;
    activeRecord.value = list.find((item) => item.paymentNo === target)
      ?? list[0] ?? null;
  },
  // 回款记录落地后卡片高度才算定型，这时再量应收表格的可用高度
  onSettled: () => armFit(),
  immediate: false,
});
const recording = ref(false);
const submitting = ref(false);
const loadingReceivables = ref(false);

const receivables = ref<any[]>([]);
const selectedReceivables = ref<any[]>([]);
/**
 * 应收表格实例：自适应条数重算会重新拉取应收数据，el-table 换 data 会连带清空勾选。
 * 勾选状态按 receivableId 在加载后复原，否则「勾了行、被重算悄悄取消勾选、
 * 点确认核销又提示请先勾选」。
 */
const receivableGridRef = ref<any>(null);
/** 应收 ID -> 本次核销金额（元字符串） */
const writeOffAmounts = reactive<Record<string, string>>({});
/** 应收台账（未结清）独立分页：不受回款记录分页影响，也不进 sessionStorage 记忆 */
const receivablePage = ref(1);
const receivableSize = ref(10);
const totalReceivables = ref(0);

/**
 * 宽屏（lg ≥ 1200px）两卡等高后，让应收表格吃满剩余高度：
 * 表头固定、列表在框内滚动，分页与核销按钮紧跟表格下方，不再靠 margin-top:auto 硬顶到底。
 * 窄屏两列堆叠时表格回到自然高度（height 不传），否则百分比高度没有参照会塌成空框。
 */
const panelFill = ref(false);
let panelMq: MediaQueryList | null = null;
function syncPanelFill(): void {
  panelFill.value = panelMq?.matches ?? false;
}

/**
 * 每页条数按表格可用高度自适应（宽屏），让 12/13 行正好填满表格框，
 * 不在表格底部留一条空白。用户手动选过条数后（autoFitSize=false）不再自作主张；
 * 下拉选项里补上自适应出来的值，否则 el-pagination 会显示空白。
 */
const receivableTableRef = ref<HTMLElement | null>(null);
const autoFitSize = ref(true);
let fitObserver: ResizeObserver | null = null;
let applyingFit = false;

const receivableSizes = computed(() => {
  const base = [5, 10, 20, 50];
  const current = receivableSize.value;
  return base.includes(current) ? base : [...base, current].sort((a, b) => a - b);
});

/** 一行数据的实际渲染高度（small 尺寸实测 34px），空表时用实测值兜底，避免首次算偏 */
function measureRowHeight(): number {
  const row = receivableTableRef.value?.querySelector('.el-table__row') as HTMLElement | null;
  return row?.offsetHeight || 34;
}

/** 表格框净高能放下多少行（减表头实测 32px），至少 5 行 */
function computeFitSize(): number {
  const wrap = receivableTableRef.value;
  if (!wrap) return receivableSize.value;
  const header = wrap.querySelector('.el-table__header-wrapper') as HTMLElement | null;
  const available = wrap.clientHeight - (header?.offsetHeight ?? 32);
  const rowHeight = measureRowHeight();
  if (available <= 0 || rowHeight <= 0) return receivableSize.value;
  return Math.max(5, Math.floor(available / rowHeight));
}

function applyFitSize(): void {
  if (!panelFill.value || !autoFitSize.value) return;
  const fit = computeFitSize();
  if (fit === receivableSize.value) return;
  applyingFit = true;
  receivableSize.value = fit;
  receivablePage.value = 1;
  void nextTick(() => { applyingFit = false; });
  void loadReceivables();
}

/**
 * 首屏会连续经历「左侧记录还没回来 → 卡片变高」几步高度变化，逐次调用会连发几个
 * size 不同的请求。用 300ms 防抖合并，并且等回款记录落地、卡片高度定型后才首次测量
 * （fitArmed），避免先按虚高的高度算出一个多余条数。
 */
let fitTimer: number | null = null;
let fitArmed = false;
function scheduleFit(): void {
  if (fitTimer !== null) window.clearTimeout(fitTimer);
  fitTimer = window.setTimeout(() => {
    fitTimer = null;
    applyFitSize();
  }, 300);
}

function armFit(): void {
  if (!fitArmed) fitArmed = true;
  scheduleFit();
}

/** 当前回款单最大可核销金额 = 回款金额 − 已核销 */
const availableCent = computed(() => {
  const record = activeRecord.value;
  if (!record) return 0;
  return Number(record.amountCent ?? 0) - Number(record.writtenOffCent ?? 0);
});

/** 单行应收剩余可核销金额（分） */
function remainingCent(row: any): number {
  return Number(row?.amountCent ?? 0) - Number(row?.writtenOffCent ?? 0);
}

function onSelectRecord(row: PaymentRecordVO | null): void {
  if (!row) return;
  // 换客户 → 应收台账回到第 1 页，否则会停在上一个客户的页码上
  if (activeRecord.value && activeRecord.value.customerId !== row.customerId) {
    receivablePage.value = 1;
  }
  activeRecord.value = row;
}

async function submitRecord(): Promise<void> {
  const amountCent = yuanToCent(recordForm.amountYuan);
  if (amountCent === null || amountCent <= 0) {
    ElMessage.warning('请填写有效的回款金额');
    return;
  }
  if (!recordForm.serialNo.trim()) {
    ElMessage.warning('请填写银行流水号');
    return;
  }
  const key = idem.getOrCreate();
  idemKey.value = key;
  recording.value = true;
  try {
    const vo = await unwrap(financeApi.record({
      customerId: Number(recordForm.customerId),
      amountCent,
      serialNo: recordForm.serialNo.trim(),
      remark: recordForm.remark || undefined,
    }, { headers: { 'Idempotency-Key': key } }));
    idem.clear();
    idemKey.value = '';
    recordForm.amountYuan = '';
    recordForm.serialNo = '';
    ElMessage.success(`回款登记成功：${vo?.paymentNo ?? ''}`);
    preferPaymentNo = vo?.paymentNo;
    await loadRecords();
    await loadReceivables();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '登记失败，可直接重试（幂等键已保留）');
  } finally {
    recording.value = false;
  }
}

/**
 * 应收台账：`openOnly=true` 由 SQL 过滤 `status <> 'CLOSED'` 并真分页。
 * 之前写死 `page:1,size:100` 再在前端剔已结清 —— 一列到底看不完，且「共 N 条」与列表行数对不上。
 */
async function loadReceivables(): Promise<void> {
  const customerId = Number(activeRecord.value?.customerId ?? recordForm.customerId);
  // 重拉数据会清空表格勾选，先记住已勾选的应收 ID，落地后按 ID 复原
  const keepSelected = selectedReceivables.value.map((row) => Number(row.receivableId));
  loadingReceivables.value = true;
  try {
    const data = await unwrap(financeApi.receivables({
      page: receivablePage.value,
      size: receivableSize.value,
      customerId,
      openOnly: true,
    }));
    receivables.value = data?.list ?? [];
    totalReceivables.value = Number(data?.total ?? 0);
    if (keepSelected.length) {
      await nextTick();
      receivables.value
        .filter((row) => keepSelected.includes(Number(row.receivableId)))
        .forEach((row) => receivableGridRef.value?.toggleRowSelection?.(row, true));
    }
  } catch (error) {
    receivables.value = [];
    totalReceivables.value = 0;
    ElMessage.error((error as Error)?.message || '应收数据加载失败');
  } finally {
    loadingReceivables.value = false;
  }
}

/** 手动点「加载该客户应收」从第 1 页看起，避免停在上次翻到的页码上 */
function reloadReceivables(): void {
  receivablePage.value = 1;
  void loadReceivables();
}

function onReceivablePage(value: number): void {
  receivablePage.value = value;
  void loadReceivables();
}

function onReceivableSize(value: number): void {
  // 用户主动选择条数 → 关掉自适应，之后窗口变化不再覆盖他的选择
  if (!applyingFit) autoFitSize.value = false;
  receivableSize.value = value;
  receivablePage.value = 1;
  void loadReceivables();
}

/**
 * 核销前置校验：把「后端必然拒绝」的输入在本地就说清楚，文案口径与后端
 * WriteOffAllocator 保持一致（都是「本次 X 元 / 上限 Y 元 / 差额 Z 元」）。
 * 不再把填了 0 或空的行静默丢掉 —— 那会让用户以为核销了却没生效。
 */
function validateWriteOff(): string | null {
  if (!activeRecord.value) return '请先选择一笔回款单';
  if (!selectedReceivables.value.length) return '请先勾选需要核销的应收单';

  let total = 0;
  for (const row of selectedReceivables.value) {
    const id = Number(row.receivableId);
    const label = row.orderNo ? `应收单 ${id}（订单 ${row.orderNo}）` : `应收单 ${id}`;
    const raw = String(writeOffAmounts[id] ?? '').trim();
    if (!raw) return `${label} 尚未填写本次核销金额`;
    const cent = yuanToCent(raw);
    if (cent === null) return `${label} 的核销金额「${raw}」不是有效金额`;
    if (cent <= 0) return `${label} 的核销金额必须大于 0 元`;
    const remaining = remainingCent(row);
    if (cent > remaining) {
      return `${label} 本次核销 ${fmtMoney(cent)}，超过该应收单剩余可核销金额 ${fmtMoney(remaining)}`;
    }
    total += cent;
  }

  if (total > availableCent.value) {
    return `本次核销合计 ${fmtMoney(total)}，超过该回款单最大可核销金额 ${fmtMoney(availableCent.value)}`
      + `（回款金额 ${fmtMoney(activeRecord.value.amountCent)}，`
      + `已核销 ${fmtMoney(activeRecord.value.writtenOffCent ?? 0)}）`;
  }
  return null;
}

function buildLines(): { receivableId: number; amount: number }[] {
  return selectedReceivables.value.map((row) => ({
    receivableId: Number(row.receivableId),
    amount: yuanToCent(String(writeOffAmounts[Number(row.receivableId)] ?? '')) ?? 0,
  }));
}

async function writeOff(): Promise<void> {
  if (!activeRecord.value) return;
  const invalid = validateWriteOff();
  if (invalid) {
    ElMessage.warning(invalid);
    return;
  }
  const lines = buildLines();
  const writtenIds = lines.map((line) => line.receivableId);
  try {
    const grant = await openConfirm({
      action: 'PAYMENT_WRITE_OFF',
      targetId: activeRecord.value.paymentNo,
      summary: `回款单 ${activeRecord.value.paymentNo} · ${lines.length} 笔应收 · 合计 ${fmtMoney(lines.reduce((sum, l) => sum + l.amount, 0))}`,
    });
    if (grant.isExpired()) {
      ElMessage.warning('确认已超时，请重新确认');
      return;
    }
    submitting.value = true;
    const vo = await unwrap(financeApi.writeOff(activeRecord.value.paymentNo, {
      customerId: Number(activeRecord.value.customerId),
      lines,
    }, grant.token));
    // 已核销行清空输入，避免下次误按旧金额重复提交
    writtenIds.forEach((id) => { delete writeOffAmounts[id]; });
    ElMessage.success(`核销完成，已关闭 ${vo?.closedReceivableIds?.length ?? 0} 笔应收`);
    await loadRecords();
    await loadReceivables();
    await loadWriteOffs();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '核销失败');
  } finally {
    submitting.value = false;
  }
}

async function reverse(): Promise<void> {
  if (!activeRecord.value) return;
  let reason = '';
  try {
    const { value } = await ElMessageBox.prompt('请填写冲销原因', '回款冲销', {
      inputValidator: (v: string) => (v && v.trim() ? true : '原因不能为空'),
      type: 'warning',
    });
    reason = String(value).trim();
  } catch {
    return;
  }
  try {
    const grant = await openConfirm({
      action: 'PAYMENT_REVERSE',
      targetId: activeRecord.value.paymentNo,
      summary: `回款单 ${activeRecord.value.paymentNo} · 金额 ${fmtMoney(activeRecord.value.amountCent)} · 原因 ${reason}`,
    });
    if (grant.isExpired()) {
      ElMessage.warning('确认已超时，请重新确认');
      return;
    }
    submitting.value = true;
    await unwrap(financeApi.reverse(activeRecord.value.paymentNo, reason, grant.token));
    ElMessage.success('回款已冲销');
    await loadRecords();
    await loadReceivables();
    await loadWriteOffs();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '冲销失败');
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  panelMq = window.matchMedia('(min-width: 1200px)');
  syncPanelFill();
  panelMq.addEventListener('change', onPanelFillChange);

  // 卡片高度定型后（loadRecords 的 finally 里）才首次测量条数；这里只挂监听
  void nextTick(() => {
    if (typeof ResizeObserver !== 'undefined') {
      fitObserver = new ResizeObserver(() => { if (fitArmed) scheduleFit(); });
      if (receivableTableRef.value) fitObserver.observe(receivableTableRef.value);
    }
  });

  void loadRecords();
  void loadWriteOffs();
  const fromQuery = String(route.query.receivableId ?? '');
  if (fromQuery) ElMessage.info(`来自应收台账：应收 ${fromQuery}，请先登记该客户的回款`);
});

/** 进出宽屏时重新量一次：窄屏表格是自然高度，不参与自适应 */
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

onBeforeUnmount(() => {
  panelMq?.removeEventListener('change', syncPanelFill);
  panelMq = null;
});
</script>

<style scoped>
.sub-title { margin: 20px 0 10px; font-size: 14px; color: #33463f; }
.section-tools { margin: 14px 0; }
.record-pager { margin-top: 12px; display: flex; justify-content: flex-end; }
/* 应收台账分页独立类名：复用 .record-pager 会让「回款记录」的既有用例命中两个分页器 */
.receivable-pager { margin-top: 12px; display: flex; justify-content: flex-end; }
/* 无应收时只隐藏不塌陷，保证自适应条数测量到的可用高度恒定 */
.pager-placeholder { visibility: hidden; }

/*
 * 双列等高（lg ≥1200px）：「核销与冲销」与「登记回款」两张卡片底边对齐。
 * 做法是让 el-col 变 flex 容器、卡片 flex:1 撑满列高；右侧应收表格 flex:1 吃掉卡片里的
 * 剩余高度（表头固定、列表框内滚动），分页与核销按钮自然紧随表格，不再留大片空白。
 * 窄屏两列堆叠时不再强制等高，否则整卡会被拉出空白。
 */
@media (min-width: 1200px) {
  .finance-split { align-items: stretch; }
  .finance-split :deep(.el-col) { display: flex; }
  .finance-split .split-panel {
    flex: 1 1 auto;
    width: 100%;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
  /* 卡片内的表单/表格不允许被压缩，否则表格行会被挤扁（应收表格区除外，它负责吃掉剩余高度） */
  .finance-split .split-panel > *:not(.receivable-table-wrap) { flex-shrink: 0; }
  .finance-split .receivable-table-wrap {
    /* basis 必须是 0：auto 会让行数撑高卡片，与左侧「登记回款」不再等高 */
    flex: 1 1 0;
    min-height: 0;
    overflow: hidden;
  }
}
</style>
