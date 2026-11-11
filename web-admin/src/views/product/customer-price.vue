<template>
  <div class="cp-page">
    <div class="cp-head">
      <div>
        <h2 class="sent-page-title">一客一价</h2>
        <p class="sent-hint cp-sub">
          为指定客户锁定某个规格的专属结算价，优先级高于批量折扣；未设置专属价时按商品售价与折扣档计算。
        </p>
      </div>
      <div class="cp-head-actions">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openDialog()">新增专属价</el-button>
      </div>
    </div>

    <div class="cp-stats">
      <div class="cp-stat">
        <span class="cp-stat-label">专属价规则</span>
        <strong class="cp-stat-value">{{ rules.length }}</strong>
        <span class="cp-stat-foot">条</span>
      </div>
      <div class="cp-stat">
        <span class="cp-stat-label">覆盖客户</span>
        <strong class="cp-stat-value">{{ customerCount }}</strong>
        <span class="cp-stat-foot">位</span>
      </div>
      <div class="cp-stat">
        <span class="cp-stat-label">覆盖规格</span>
        <strong class="cp-stat-value">{{ skuCount }}</strong>
        <span class="cp-stat-foot">个</span>
      </div>
      <div class="cp-stat">
        <span class="cp-stat-label">当前生效</span>
        <strong class="cp-stat-value cp-stat-value--accent">{{ activeCount }}</strong>
        <span class="cp-stat-foot">条</span>
      </div>
    </div>

    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="filtered"
        :loading="loading"
        :operation-width="118"
        exportable
        export-name="一客一价"
        @refresh="load"
      >
        <template #toolbar>
          <el-select
            v-model="filter.customerId"
            placeholder="搜索客户姓名或手机号"
            clearable
            filterable
            remote
            :remote-method="searchCustomers"
            :loading="customerLoading"
            style="width: 220px"
          >
            <el-option
              v-for="c in customers"
              :key="String(c.customerId)"
              :label="customerLabel(c)"
              :value="String(c.customerId)"
            />
          </el-select>
          <el-select
            v-model="filter.productId"
            placeholder="选择商品"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="p in products"
              :key="String(p.productId)"
              :label="p.name"
              :value="String(p.productId)"
            />
          </el-select>
          <el-select v-model="filter.state" placeholder="生效状态" clearable style="width: 130px">
            <el-option label="生效中" value="ACTIVE" />
            <el-option label="未生效" value="PENDING" />
            <el-option label="已过期" value="EXPIRED" />
          </el-select>
          <el-button @click="resetFilter">重置</el-button>
        </template>

        <template #col-customer="{ row }">
          <div class="cp-cell">
            <span class="cp-cell-main" :title="row.customerName || ''">
              {{ row.customerName || `客户 #${row.customerId}` }}
            </span>
            <span v-if="row.customerPhone" class="cp-cell-sub">{{ row.customerPhone }}</span>
          </div>
        </template>

        <template #col-product="{ row }">
          <div class="cp-cell">
            <span class="cp-cell-main" :title="row.productName || ''">
              {{ row.productName || `SKU #${row.skuId}` }}
            </span>
            <span class="cp-cell-sub">{{ specText(row) }}</span>
          </div>
        </template>

        <template #col-retailPriceCent="{ row }">
          <span class="cp-retail">{{ money(row.retailPriceCent) }}</span>
        </template>

        <template #col-priceCent="{ row }">
          <div class="cp-cell">
            <span class="cp-price">{{ money(row.priceCent) }}</span>
            <span v-if="discountOf(row) !== null" class="cp-cell-sub">{{ discountOf(row) }} 折</span>
          </div>
        </template>

        <template #col-valid="{ row }">
          <div class="cp-cell">
            <span class="cp-cell-main">{{ validStart(row) }}</span>
            <span class="cp-cell-sub">{{ validEnd(row) }}</span>
          </div>
        </template>

        <template #col-state="{ row }">
          <StatusTag :label="stateText(row)" :tone="stateTone(row)" />
        </template>

        <template #operations="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该专属价？" @confirm="remove(row)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>

        <template #empty>
          <div class="cp-empty">
            <el-empty description="" :image-size="72">
              <template #description>
                <p class="cp-empty-title">还没有任何专属价</p>
                <p class="cp-empty-desc">
                  先选客户、再选商品规格，填一个低于售价的专属价，就能为老客户锁定价格。
                </p>
              </template>
              <el-button type="primary" @click="openDialog()">新增第一条专属价</el-button>
            </el-empty>
          </div>
        </template>
      </SentTable>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑专属价' : '新增专属价'"
      width="600px"
      destroy-on-close
    >
      <el-form :model="form" label-width="96px">
        <el-form-item label="客户" required>
          <el-select
            v-model="form.customerId"
            filterable
            remote
            :remote-method="searchCustomers"
            :loading="customerLoading"
            placeholder="搜索客户姓名或手机号"
            style="width: 100%"
            :disabled="!!editingId"
          >
            <el-option
              v-for="c in customers"
              :key="String(c.customerId)"
              :label="customerLabel(c)"
              :value="String(c.customerId)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="商品" required>
          <el-select
            v-model="form.productId"
            filterable
            placeholder="搜索商品名称"
            style="width: 100%"
            :disabled="!!editingId"
            @change="onProductChange"
          >
            <el-option
              v-for="p in products"
              :key="String(p.productId)"
              :label="p.name"
              :value="String(p.productId)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="规格" required>
          <el-select
            v-model="form.skuId"
            placeholder="选择规格"
            style="width: 100%"
            :disabled="!!editingId || !form.productId"
            :loading="skuLoading"
            @change="onSkuChange"
          >
            <el-option
              v-for="s in skuOptions"
              :key="String(s.skuId)"
              :label="skuLabel(s)"
              :value="String(s.skuId)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="当前售价">
          <span class="cp-retail">{{ money(currentRetail) }}</span>
          <span class="sent-hint" style="margin-left: 10px">专属价通常应低于该价格</span>
        </el-form-item>

        <el-form-item label="专属价" required>
          <el-input-number
            v-model="form.priceYuan"
            :min="0"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 180px"
          />
          <span class="cp-unit">元</span>
          <span v-if="formDiscount !== null" class="cp-discount-hint">
            相当于 {{ formDiscount }} 折
          </span>
        </el-form-item>

        <el-form-item label="有效期">
          <el-date-picker
            v-model="form.validRange"
            type="daterange"
            value-format="x"
            start-placeholder="生效日"
            end-placeholder="失效日"
            style="width: 100%"
          />
          <span class="sent-hint">不选表示长期有效</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 一客一价（重做）：客户 / 商品 / 规格三级串联，列表直接展示「谁 · 买什么 · 多少钱 · 何时有效」。
 * 列表的客户名、商品名、规格与零售价由后端联查返回，前端不再拿裸 ID 展示。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtCent, fmtDateTime, yuanToCent } from '@sent/shared';
import type { CustomerVO, CustomerPriceVO } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { customerApi, productApi } from '@/api';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

interface ProductOption {
  productId: string;
  name: string;
}
interface SkuOption {
  skuId: string;
  skuCode: string;
  spec: string;
  retailPriceCent: number;
}

const columns: TableColumn[] = [
  {
    prop: 'customer',
    label: '客户',
    minWidth: 124,
    formatter: (row: any) => `${row.customerName ?? ''} ${row.customerPhone ?? ''}`.trim(),
  },
  {
    prop: 'product',
    label: '商品 / 规格',
    minWidth: 168,
    formatter: (row: any) => `${row.productName ?? ''} ${specText(row)}`.trim(),
  },
  { prop: 'retailPriceCent', label: '售价', width: 80, align: 'right', formatter: (_r, v) => money(v) },
  {
    prop: 'priceCent',
    label: '专属价',
    width: 96,
    align: 'right',
    formatter: (_r, v) => money(v),
  },
  {
    prop: 'valid',
    label: '有效期',
    minWidth: 124,
    formatter: (row: any) => validityText(row),
  },
  {
    prop: 'state',
    label: '状态',
    width: 84,
    align: 'center',
    formatter: (row: any) => stateText(row),
  },
];

const rules = ref<CustomerPriceVO[]>([]);
const customers = ref<CustomerVO[]>([]);
const products = ref<ProductOption[]>([]);
const skuOptions = ref<SkuOption[]>([]);
const skuLoading = ref(false);
const customerLoading = ref(false);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const currentRetail = ref(0);

const filter = reactive<{ customerId: string; productId: string; state: string }>({
  customerId: '',
  productId: '',
  state: '',
});

const form = reactive({
  customerId: '',
  productId: '',
  skuId: '',
  priceYuan: 0,
  validRange: [] as string[],
});

const customerCount = computed(
  () => new Set(rules.value.map((r) => String(r.customerId))).size,
);
const skuCount = computed(() => new Set(rules.value.map((r) => String(r.skuId))).size);
const activeCount = computed(() => rules.value.filter((r) => stateOf(r) === 'ACTIVE').length);

const filtered = computed(() =>
  rules.value.filter((r) => {
    if (filter.customerId && String(r.customerId) !== filter.customerId) return false;
    if (filter.productId && String(r.productId ?? '') !== filter.productId) return false;
    if (filter.state && stateOf(r) !== filter.state) return false;
    return true;
  }),
);

const formDiscount = computed(() => {
  if (!currentRetail.value || !form.priceYuan) return null;
  const rate = (form.priceYuan * 100) / (currentRetail.value / 100);
  return rate > 0 && rate < 100 ? (rate / 10).toFixed(1) : null;
});

function money(cent?: number | null): string {
  return `¥${fmtCent(Number(cent ?? 0))}`;
}

function customerLabel(c: CustomerVO): string {
  return c.phone ? `${c.name} · ${c.phone}` : c.name;
}

function specText(row: CustomerPriceVO): string {
  const spec = row.spec?.trim();
  if (spec) return `${spec}${row.skuCode ? ` · ${row.skuCode}` : ''}`;
  return row.skuCode || `规格 #${row.skuId}`;
}

function skuLabel(s: SkuOption): string {
  const parts = [s.spec || `规格 ${s.skuId}`];
  if (s.skuCode) parts.push(s.skuCode);
  parts.push(money(s.retailPriceCent));
  return parts.join(' · ');
}

function discountOf(row: CustomerPriceVO): string | null {
  const retail = Number(row.retailPriceCent ?? 0);
  if (!retail || !row.priceCent) return null;
  const rate = (row.priceCent * 100) / retail;
  return rate > 0 && rate < 100 ? (rate / 10).toFixed(1) : null;
}

function stateOf(row: CustomerPriceVO): 'ACTIVE' | 'PENDING' | 'EXPIRED' {
  const now = Date.now();
  const from = Number(row.validFromMillis ?? 0);
  const to = Number(row.validToMillis ?? 0);
  if (from && now < from) return 'PENDING';
  if (to && now > to) return 'EXPIRED';
  return 'ACTIVE';
}

function stateText(row: CustomerPriceVO): string {
  const state = stateOf(row);
  if (state === 'PENDING') return '未生效';
  if (state === 'EXPIRED') return '已过期';
  return '生效中';
}

function stateTone(row: CustomerPriceVO): 'success' | 'warning' | 'info' {
  const state = stateOf(row);
  if (state === 'PENDING') return 'warning';
  if (state === 'EXPIRED') return 'info';
  return 'success';
}

/** 有效期拆两行短格式（日期即可），避免长串时间把列撑爆。 */
function dateOnly(millis: number): string {
  return fmtDateTime(millis).slice(0, 10);
}

function validStart(row: CustomerPriceVO): string {
  const from = Number(row.validFromMillis ?? 0);
  return from ? `${dateOnly(from)} 起` : '长期有效';
}

function validEnd(row: CustomerPriceVO): string {
  const to = Number(row.validToMillis ?? 0);
  return to ? `至 ${dateOnly(to)}` : '不设终止';
}

function validityText(row: CustomerPriceVO): string {
  const from = Number(row.validFromMillis ?? 0);
  const to = Number(row.validToMillis ?? 0);
  if (!from && !to) return '长期有效';
  const start = from ? fmtDateTime(from) : '立即';
  const end = to ? fmtDateTime(to) : '长期';
  return `${start} ~ ${end}`;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    rules.value = (await unwrap(productApi.listCustomerPrices({}))) ?? [];
  } catch (error) {
    rules.value = [];
    ElMessage.error((error as Error)?.message || '专属价列表加载失败');
  } finally {
    loading.value = false;
  }
}

/** 客户量可达数百，不能一次拉全量：下拉走远程搜索，按姓名/手机号/编号查。 */
async function searchCustomers(keyword: string): Promise<void> {
  customerLoading.value = true;
  try {
    const page = await unwrap(
      customerApi.list({ page: 1, size: 20, keyword: keyword || undefined }),
    );
    customers.value = page?.list ?? [];
  } catch {
    customers.value = [];
  } finally {
    customerLoading.value = false;
  }
}

async function loadOptions(): Promise<void> {
  await searchCustomers('');
  try {
    const page = await unwrap(productApi.list({ page: 1, size: 100 }));
    products.value = (page?.list ?? []).map((p: any) => ({
      productId: String(p.productId),
      name: String(p.name),
    }));
  } catch {
    products.value = [];
  }
}

/** 选中商品后才按需拉该商品的规格，避免一次拉全站 SKU。 */
async function loadSkus(productId: string): Promise<void> {
  skuOptions.value = [];
  if (!productId) return;
  skuLoading.value = true;
  try {
    const detail = await unwrap(productApi.detail(productId));
    skuOptions.value = (detail?.skus ?? []).map((s) => ({
      skuId: String(s.skuId),
      skuCode: s.skuCode ?? '',
      spec: s.spec ?? '',
      retailPriceCent: Number(s.price?.retailPriceCent ?? 0),
    }));
  } catch {
    skuOptions.value = [];
  } finally {
    skuLoading.value = false;
  }
}

function onProductChange(productId: string): void {
  form.skuId = '';
  currentRetail.value = 0;
  void loadSkus(productId);
}

function onSkuChange(skuId: string): void {
  const sku = skuOptions.value.find((s) => s.skuId === skuId);
  currentRetail.value = sku?.retailPriceCent ?? 0;
}

function resetFilter(): void {
  filter.customerId = '';
  filter.productId = '';
  filter.state = '';
}

async function openDialog(row?: CustomerPriceVO): Promise<void> {
  editingId.value = row ? Number(row.id) : null;
  form.customerId = row ? String(row.customerId) : '';
  form.productId = row ? String(row.productId ?? '') : '';
  form.skuId = row ? String(row.skuId) : '';
  form.priceYuan = row ? Number(row.priceCent) / 100 : 0;
  form.validRange =
    row?.validFromMillis && row?.validToMillis
      ? [String(row.validFromMillis), String(row.validToMillis)]
      : [];
  currentRetail.value = Number(row?.retailPriceCent ?? 0);
  // 编辑时把当前客户补进选项，避免远程搜索列表里没有他而显示成裸 ID
  if (row && !customers.value.some((c) => String(c.customerId) === String(row.customerId))) {
    customers.value = [
      {
        customerId: row.customerId,
        name: row.customerName || `客户 ${row.customerId}`,
        phone: row.customerPhone ?? '',
        type: 'PERSON',
      } as CustomerVO,
      ...customers.value,
    ];
  }
  dialogVisible.value = true;
  if (row?.productId) {
    await loadSkus(String(row.productId));
  } else {
    skuOptions.value = [];
  }
}

async function submit(): Promise<void> {
  const priceCent = yuanToCent(form.priceYuan);
  if (!form.customerId || !form.skuId || priceCent === null || priceCent <= 0) {
    ElMessage.warning('请选择客户、商品规格并填写专属价');
    return;
  }
  const [from, to] = form.validRange;
  saving.value = true;
  try {
    await unwrap(
      productApi.upsertCustomerPrice({
        skuId: Number(form.skuId),
        customerId: Number(form.customerId),
        priceCent,
        validFromMillis: from ? Number(from) : 0,
        validToMillis: to ? Number(to) : 0,
      }),
    );
    ElMessage.success('专属价已保存');
    dialogVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function remove(row: CustomerPriceVO): Promise<void> {
  try {
    await unwrap(productApi.removeCustomerPrice(Number(row.id)));
    ElMessage.success('已删除');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '删除失败');
  }
}

onMounted(async () => {
  await Promise.all([load(), loadOptions()]);
});
</script>

<style scoped>
.cp-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.cp-sub {
  margin: -8px 0 14px;
  max-width: 720px;
  line-height: 1.7;
}
.cp-head-actions {
  display: flex;
  gap: 8px;
  padding-top: 2px;
}
.cp-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.cp-stat {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  align-items: baseline;
  gap: 6px;
  border-left: 3px solid #12664f;
}
.cp-stat-label {
  font-size: 13px;
  color: #6b7a74;
  flex: 1;
}
.cp-stat-value {
  font-size: 24px;
  line-height: 1;
  color: #1f2d28;
}
.cp-stat-value--accent { color: #ff7a1a; }
.cp-stat-foot { font-size: 12px; color: #9aa8a2; }
.cp-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}
.cp-cell-main { color: #1f2d28; }
.cp-cell-sub { font-size: 12px; color: #9aa8a2; }
.cp-retail { color: #6b7a74; }
.cp-price { font-weight: 600; color: #12664f; }
.cp-unit { margin-left: 6px; color: #6b7a74; }
.cp-discount-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #ff7a1a;
  background: #fff2e8;
  padding: 2px 8px;
  border-radius: 10px;
}
.cp-empty { padding: 26px 0 34px; }
.cp-empty-title { margin: 0 0 6px; font-size: 15px; color: #1f2d28; }
.cp-empty-desc { margin: 0 0 16px; font-size: 13px; color: #9aa8a2; }
@media (max-width: 1100px) {
  .cp-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
