<template>
  <div>
    <h2 class="sent-page-title">发货工作台</h2>
    <el-row :gutter="16">
      <el-col :xs="24" :lg="13">
        <div class="sent-panel">
          <h3 class="section-title">待发货订单</h3>
          <SentTable
            :columns="columns"
            :rows="rows"
            :total="total"
            :page="page"
            :size="size"
            :loading="loading"
            exportable
            export-name="待发货订单"
            :operation-width="110"
            @update:page="onPage"
            @update:size="onSize"
            @refresh="load"
          >
            <template #toolbar>
              <!-- 发货工作台恒定只查可发货状态，不提供 clearable：清空会把 COMPLETED 单放回列表并触发 409 -->
              <el-select v-model="status" placeholder="状态" style="width: 150px" @change="onSearch">
                <el-option label="待发货" :value="OrderStatus.WAIT_SHIP" />
                <el-option label="部分发货" :value="OrderStatus.PART_SHIPPED" />
              </el-select>
              <el-button type="primary" @click="onSearch">查询</el-button>
            </template>
            <template #col-status="{ row }"><StatusTag :status="row.status" /></template>
            <template #operations="{ row }">
              <el-button link type="primary" @click="selectOrder(row)">选择发货</el-button>
            </template>
          </SentTable>
        </div>
      </el-col>

      <el-col :xs="24" :lg="11">
        <div class="sent-panel">
          <h3 class="section-title">发货单</h3>
          <el-alert
            v-if="!current"
            title="请从左侧选择一笔待发货订单"
            type="info"
            :closable="false"
            show-icon
          />
          <template v-else>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
              <el-descriptions-item label="应付金额">{{ fmtMoney(current.payableCent) }}</el-descriptions-item>
              <el-descriptions-item label="幂等键">{{ idemKey || '提交时生成' }}</el-descriptions-item>
            </el-descriptions>

            <el-form label-width="90px" class="ship-form">
              <el-form-item label="承运商">
                <el-input v-model="form.carrier" placeholder="如：顺丰速运" clearable />
              </el-form-item>
              <el-form-item label="运单号">
                <el-input v-model="form.trackingNo" placeholder="快递单号" clearable />
              </el-form-item>
              <el-form-item label="发货商品">
                <div v-loading="loadingLines" class="ship-lines">
                  <el-alert
                    v-if="!loadingLines && !lines.length"
                    title="该订单没有可发货的商品行"
                    type="warning"
                    :closable="false"
                    show-icon
                  />
                  <el-alert
                    v-if="hasBlockedCustom"
                    title="含定制未完工的商品行，请先在定制审核台完成「转生产 / 标记完工」后再发货"
                    type="warning"
                    :closable="false"
                    show-icon
                    class="line-alert"
                  />
                  <div v-for="(line, index) in lines" :key="index" class="ship-line">
                    <el-checkbox v-model="line.selected" :disabled="!canShip(line)" />
                    <div class="line-info">
                      <div class="line-name">
                        {{ line.skuName }} <small v-if="line.spec">{{ line.spec }}</small>
                      </div>
                      <div class="line-meta">
                        SKU {{ line.skuId }} · 共 {{ line.qty }} 件 · 已发 {{ line.shippedQty }} · 可发 {{ line.maxQty }}
                      </div>
                    </div>
                    <el-input-number
                      v-model="line.shipQty"
                      :min="0"
                      :max="line.maxQty"
                      :controls="false"
                      :disabled="!canShip(line) || !line.selected"
                      size="small"
                      style="width: 82px"
                    />
                    <el-tag
                      v-if="line.customStatus !== 'NONE'"
                      :type="isReady(line) ? 'success' : 'warning'"
                      size="small"
                    >{{ customLabel(line.customStatus) }}</el-tag>
                  </div>
                </div>
              </el-form-item>
            </el-form>

            <div class="sent-form-row">
              <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
                确认发货
              </el-button>
              <el-button @click="resetForm">重置</el-button>
              <span class="sent-hint">失败重试复用幂等键，成功后自动换键</span>
            </div>
          </template>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
/**
 * 发货工作台（P6-35）：左列表选单、右表单按商品行发货（支持多次部分发货）。
 *
 * 修复（2026-11-08）：此前发货行是手填 SKU ID 且默认写死 1，用户几乎必然提交不存在的订单行；
 * 同时左侧列表的 status 被后端忽略，前几页全是 COMPLETED 单，选中发货必然 409。
 * 现在选中订单即拉详情自动填充真实商品行，并前置拦截「定制未完工」的合法门禁。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { orderApi, type ShipLineCmd } from '@/api';
import { OrderStatus, fmtMoney, type AdminOrderDetailVO } from '@sent/shared';
import { useIdempotencyKey } from '@/utils/idempotent';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

interface ShippableLine {
  skuId: number;
  skuName: string;
  spec: string;
  qty: number;
  shippedQty: number;
  maxQty: number;
  customStatus: string;
  selected: boolean;
  shipQty: number;
}

const CUSTOM_LABELS: Record<string, string> = {
  WAIT_DRAFT: '待传图稿',
  WAIT_REVIEW: '待审核',
  REJECTED: '已驳回',
  APPROVED: '审核通过',
  IN_PRODUCTION: '生产中',
  FINISHED: '已完工',
  CANCELLED: '已取消',
};

const columns: TableColumn[] = [
  { prop: 'orderNo', label: '订单号', minWidth: 190 },
  { prop: 'status', label: '状态', width: 120, align: 'center' },
  { prop: 'payableCent', label: '应付金额', width: 130, align: 'right',
    formatter: (_row, value) => fmtMoney(value) },
];

const rows = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const loadingLines = ref(false);
const status = ref<string>(OrderStatus.WAIT_SHIP);
const current = ref<any>(null);
const submitting = ref(false);

const idem = useIdempotencyKey('order_ship');
const idemKey = ref<string>('');

const form = reactive<{ carrier: string; trackingNo: string }>({ carrier: '', trackingNo: '' });
const lines = reactive<ShippableLine[]>([]);

function isReady(line: ShippableLine): boolean {
  // 后端 CustomizeShipGatewayImpl：无定制记录或 FINISHED 才可发，其余状态发货会 409
  return line.customStatus === 'NONE' || line.customStatus === 'FINISHED';
}
function canShip(line: ShippableLine): boolean {
  return line.maxQty > 0 && isReady(line);
}
function customLabel(value: string): string {
  return CUSTOM_LABELS[value] ?? value;
}

const hasBlockedCustom = computed(() =>
  lines.some((line) => line.maxQty > 0 && !isReady(line)));
const canSubmit = computed(() => !submitting.value
  && lines.some((line) => line.selected && canShip(line) && line.shipQty > 0));

function buildLines(source: AdminOrderDetailVO['lines']): ShippableLine[] {
  return (source ?? [])
    .filter((row) => Number(row.skuId) > 0)
    .map((row) => {
      const qty = Number(row.qty ?? 0);
      const shippedQty = Number(row.shippedQty ?? 0);
      const maxQty = Math.max(0, qty - shippedQty);
      const customStatus = String(row.customStatus ?? 'NONE');
      const candidate: ShippableLine = {
        skuId: Number(row.skuId),
        skuName: String(row.skuName ?? `SKU ${row.skuId}`),
        spec: String(row.spec ?? ''),
        qty,
        shippedQty,
        maxQty,
        customStatus,
        selected: false,
        shipQty: 0,
      };
      candidate.selected = canShip(candidate);
      candidate.shipQty = candidate.selected ? maxQty : 0;
      return candidate;
    });
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(orderApi.list({
      page: page.value,
      size: size.value,
      // 兜底：状态为空也只查待发货，绝不把全表订单放回工作台
      status: status.value || OrderStatus.WAIT_SHIP,
    }));
    rows.value = data?.list ?? [];
    total.value = Number(data?.total ?? 0);
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '待发货订单加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadLines(orderNo: string): Promise<void> {
  loadingLines.value = true;
  try {
    const detail = await unwrap(orderApi.detail(orderNo));
    lines.splice(0, lines.length, ...buildLines(detail?.lines ?? []));
  } catch (error) {
    lines.splice(0, lines.length);
    ElMessage.error((error as Error)?.message || '订单商品行加载失败');
  } finally {
    loadingLines.value = false;
  }
}

async function selectOrder(row: any): Promise<void> {
  current.value = row;
  await resetForm();
}

async function resetForm(): Promise<void> {
  form.carrier = '';
  form.trackingNo = '';
  lines.splice(0, lines.length);
  idem.reset();
  idemKey.value = idem.current() ?? '';
  if (current.value) await loadLines(String(current.value.orderNo));
}

function validLines(): ShipLineCmd[] | null {
  const result = lines
    .filter((line) => line.selected && canShip(line) && line.shipQty > 0)
    .map((line) => ({ skuId: line.skuId, qty: Math.min(line.shipQty, line.maxQty) }));
  return result.length ? result : null;
}

async function submit(): Promise<void> {
  if (!current.value) return;
  const shipLines = validLines();
  if (!shipLines) {
    ElMessage.warning('请至少勾选一条可发货的商品行并填写发货数量');
    return;
  }
  submitting.value = true;
  const key = idem.getOrCreate();
  idemKey.value = key;
  try {
    const result = await unwrap(orderApi.ship(String(current.value.orderNo), {
      carrier: form.carrier || undefined,
      trackingNo: form.trackingNo || undefined,
      lines: shipLines,
    }, { headers: { 'Idempotency-Key': key } }));
    ElMessage.success(`发货成功，订单状态 ${result?.status ?? '已更新'}`);
    idem.clear();
    idemKey.value = '';
    current.value = null;
    lines.splice(0, lines.length);
    await load();
  } catch (error) {
    // 失败保留原键，用户改完参数重试仍是同一笔
    ElMessage.error((error as Error)?.message || '发货失败，可直接重试（幂等键已保留）');
  } finally {
    submitting.value = false;
  }
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
  size.value = value;
  page.value = 1;
  void load();
}

onMounted(() => { void load(); });
</script>

<style scoped>
.section-title { margin: 0 0 14px; font-size: 15px; color: #33463f; }
.ship-form { margin-top: 16px; }
.ship-lines { width: 100%; min-height: 40px; }
.ship-line {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #eef2f0;
}
.line-info { flex: 1; min-width: 0; }
.line-name { font-size: 13px; color: #33463f; }
.line-name small { color: #8a9a94; }
.line-meta { font-size: 12px; color: #8a9a94; margin-top: 2px; }
.line-alert { margin-bottom: 10px; }
</style>
