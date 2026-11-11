<template>
  <div>
    <h2 class="sent-page-title">回款登记与核销</h2>
    <el-row :gutter="16">
      <el-col :xs="24" :lg="10">
        <div class="sent-panel">
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
            <el-checkbox v-model="onlyCurrentCustomer" @change="onFilterChange">仅看当前客户</el-checkbox>
            <span class="sent-hint">共 {{ totalRecords }} 笔回款，分页查看</span>
          </div>
          <el-table :data="records" border size="small" highlight-current-row @current-change="onSelectRecord">
            <el-table-column prop="paymentNo" label="回款单号" min-width="160" />
            <el-table-column label="金额" width="120" align="right">
              <template #default="scope">{{ fmtMoney(scope.row.amountCent) }}</template>
            </el-table-column>
            <el-table-column label="已核销" width="110" align="right">
              <template #default="scope">{{ fmtMoney(scope.row.writtenOffCent ?? 0) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="center">
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
        <div class="sent-panel">
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

          <el-table
            :data="receivables"
            border
            size="small"
            row-key="receivableId"
            v-loading="loadingReceivables"
            @selection-change="selectedReceivables = $event"
          >
            <el-table-column type="selection" width="44" />
            <el-table-column prop="receivableId" label="应收 ID" width="110" />
            <el-table-column prop="orderNo" label="订单号" min-width="150" />
            <el-table-column label="未核销" width="120" align="right">
              <template #default="scope">
                {{ fmtMoney(remainingCent(scope.row)) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="scope"><StatusTag :status="scope.row.status" /></template>
            </el-table-column>
            <el-table-column label="本次核销(元)" width="150">
              <template #default="scope">
                <el-input v-model="writeOffAmounts[scope.row.receivableId]" size="small" placeholder="0.00" />
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无未结清应收" :image-size="50" />
            </template>
          </el-table>

          <div v-if="totalReceivables > 0" class="record-pager receivable-pager">
            <el-pagination
              background
              small
              layout="total, sizes, prev, pager, next"
              :total="totalReceivables"
              :current-page="receivablePage"
              :page-size="receivableSize"
              :page-sizes="[5, 10, 20, 50]"
              @current-change="onReceivablePage"
              @size-change="onReceivableSize"
            />
          </div>

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
      </el-col>
    </el-row>
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
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { financeApi, type PaymentRecordVO } from '@/api';
import { fmtMoney, yuanToCent } from '@sent/shared';
import { openConfirm } from '@/utils/confirm';
import { useIdempotencyKey } from '@/utils/idempotent';
import { usePagination } from '@/composables/usePagination';
import StatusTag from '@/components/StatusTag.vue';

const route = useRoute();
const idem = useIdempotencyKey('payment_record');
const idemKey = ref('');

const recordForm = reactive({
  customerId: Number(route.query.customerId ?? 1),
  amountYuan: '',
  serialNo: '',
  remark: '',
});
const records = ref<PaymentRecordVO[]>([]);
const activeRecord = ref<PaymentRecordVO | null>(null);
const totalRecords = ref(0);
const onlyCurrentCustomer = ref(false);
/** 回款记录分页：真库 483 笔，必须翻页查看；页码/每页条数按路由记忆。 */
const { page, size } = usePagination({ defaultSize: 10 });
const recording = ref(false);
const submitting = ref(false);
const loadingRecords = ref(false);
const loadingReceivables = ref(false);

const receivables = ref<any[]>([]);
const selectedReceivables = ref<any[]>([]);
/** 应收 ID -> 本次核销金额（元字符串） */
const writeOffAmounts = reactive<Record<string, string>>({});
/** 应收台账（未结清）独立分页：不受回款记录分页影响，也不进 sessionStorage 记忆 */
const receivablePage = ref(1);
const receivableSize = ref(10);
const totalReceivables = ref(0);

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

/** 真库回款列表：刷新后尽量保持当前选中行（登记/核销/冲销后状态会变）。 */
async function loadRecords(preferPaymentNo?: string): Promise<void> {
  loadingRecords.value = true;
  try {
    const data = await unwrap(financeApi.paymentRecords({
      page: page.value,
      size: size.value,
      customerId: onlyCurrentCustomer.value ? Number(recordForm.customerId) || undefined : undefined,
    }));
    records.value = data?.list ?? [];
    // Jackson 把 Long 序列化成字符串，分页器只收 Number
    totalRecords.value = Number(data?.total ?? 0);
    const target = preferPaymentNo ?? activeRecord.value?.paymentNo;
    activeRecord.value = records.value.find((item) => item.paymentNo === target)
      ?? records.value[0] ?? null;
  } catch (error) {
    records.value = [];
    totalRecords.value = 0;
    ElMessage.error((error as Error)?.message || '回款列表加载失败');
  } finally {
    loadingRecords.value = false;
  }
}

/** 切客户过滤条件要回到第 1 页，否则会停在「新结果集里不存在的页码」上显示空表。 */
function onFilterChange(): void {
  page.value = 1;
  void loadRecords();
}

function onPageChange(value: number): void {
  page.value = value;
  void loadRecords();
}

function onSizeChange(value: number): void {
  size.value = value;
  page.value = 1;
  void loadRecords();
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
    await loadRecords(vo?.paymentNo);
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
  } catch (error) {
    ElMessage.error((error as Error)?.message || '冲销失败');
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  void loadRecords();
  const fromQuery = String(route.query.receivableId ?? '');
  if (fromQuery) ElMessage.info(`来自应收台账：应收 ${fromQuery}，请先登记该客户的回款`);
});
</script>

<style scoped>
.section-title { margin: 0 0 14px; font-size: 15px; color: #33463f; }
.sub-title { margin: 20px 0 10px; font-size: 14px; color: #33463f; }
.section-tools { margin: 14px 0; }
.record-pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
