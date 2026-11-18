<template>
  <div>
    <h2 class="sent-page-title">优惠券创建</h2>

    <div class="sent-panel">
      <el-alert
        v-if="!loading && !rows.length"
        title="还没有自建券，点击右上角「新建优惠券」创建一张（可限定指定商品或规格）"
        type="info"
        :closable="false"
        show-icon
        class="list-alert"
      />
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :selectable="deleteMode"
        row-key="templateId"
        exportable
        export-name="优惠券模板"
        :export-rows="fetchAllTemplates"
        :operation-width="120"
        @selection-change="selected = $event"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: any) => String(r.templateId))"
            :summary="`已选择 ${selected.length} 张优惠券：${selected.map((r: any) => r.templateNo || r.name).join('、')}`"
            :delete-fn="(ids, config) => unwrap(couponApi.batchDeleteTemplates(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #toolbar>
          <el-input
            v-model="keyword"
            placeholder="券号 / 券名称"
            clearable
            style="width: 220px"
            data-testid="coupon-template-keyword"
            @keyup.enter="onSearch"
          />
          <el-select
            v-model="couponType"
            clearable
            placeholder="全部券型"
            style="width: 150px"
            data-testid="coupon-template-type-filter"
            @change="onSearch"
          >
            <el-option v-for="t in TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button type="primary" data-testid="coupon-template-create" @click="openCreate">
            新建优惠券
          </el-button>
        </template>
        <template #col-name="{ row }">
          <span>{{ row.name }}</span>
          <span class="type-tag">{{ typeText(row.couponType) }}</span>
        </template>
        <template #col-scopeType="{ row }">{{ scopeText(row) }}</template>
        <template #col-claimedQty="{ row }">
          {{ row.claimable === 1 ? `${Number(row.claimedQty)} / ${Number(row.totalQty) < 0 ? '不限' : Number(row.totalQty)}` : '—' }}
        </template>
        <template #col-status="{ row }">
          <StatusTag
            :status="row.status"
            :label="row.status === 'ACTIVE' ? '启用' : '停用'"
            :tone="row.status === 'ACTIVE' ? 'success' : 'info'"
          />
        </template>
        <template #operations="{ row }">
          <el-button link type="primary" data-testid="coupon-template-issue" @click="goIssue(row)">
            去发放
          </el-button>
        </template>
      </SentTable>
    </div>

    <el-dialog
      v-model="visible"
      title="新建优惠券"
      width="640px"
      top="6vh"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-width="110px" class="create-form">
        <el-form-item label="券名称" required>
          <el-input
            v-model="form.name"
            maxlength="64"
            show-word-limit
            placeholder="如：斯伯丁篮球专享 9 折"
            data-testid="coupon-create-name"
          />
        </el-form-item>

        <el-form-item label="券类型">
          <el-radio-group v-model="form.couponType" data-testid="coupon-create-type">
            <el-radio v-for="t in TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="优惠方式">
          <el-radio-group v-model="form.discountType" data-testid="coupon-create-discount-type">
            <el-radio value="AMOUNT">满减 / 立减金额</el-radio>
            <el-radio value="PERCENT">折扣</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.discountType === 'AMOUNT'" label="券面额" required>
          <el-input-number
            v-model="form.amountYuan"
            :min="0.01"
            :max="99999"
            :precision="2"
            :step="1"
            style="width: 180px"
            data-testid="coupon-create-amount"
          />
          <span class="sent-hint">元</span>
        </el-form-item>
        <el-form-item v-else label="折扣" required>
          <el-input-number
            v-model="form.discountRate"
            :min="1"
            :max="99"
            :step="1"
            style="width: 180px"
            data-testid="coupon-create-rate"
          />
          <span class="sent-hint">
            输入支付比例：90 = 9 折、88 = 8.8 折（{{ rateText(form.discountRate) }}）
          </span>
        </el-form-item>

        <el-form-item label="使用门槛">
          <el-input-number
            v-model="form.thresholdYuan"
            :min="0"
            :max="999999"
            :precision="2"
            :step="10"
            :disabled="form.couponType === 'NO_THRESHOLD'"
            style="width: 180px"
            data-testid="coupon-create-threshold"
          />
          <span class="sent-hint">
            {{ form.couponType === 'NO_THRESHOLD' ? '无门槛券不需要门槛' : '元，满减券与企业券必须大于 0' }}
          </span>
        </el-form-item>

        <el-form-item label="适用商品">
          <el-radio-group v-model="form.scopeType" data-testid="coupon-create-scope">
            <el-radio value="ALL">全部商品</el-radio>
            <el-radio value="SPU">指定商品</el-radio>
            <el-radio value="SKU">指定规格</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.scopeType !== 'ALL'" label="选择商品">
          <el-select
            v-model="form.productIds"
            multiple
            filterable
            remote
            clearable
            :remote-method="searchProducts"
            :loading="productLoading"
            placeholder="输入商品名搜索"
            style="width: 100%"
            data-testid="coupon-create-products"
          >
            <el-option
              v-for="p in productOptions"
              :key="String(p.productId)"
              :label="p.name"
              :value="Number(p.productId)"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.scopeType === 'SKU'" label="选择规格">
          <el-select
            v-model="form.skuIds"
            multiple
            clearable
            :loading="skuLoading"
            :disabled="form.productIds.length !== 1"
            :placeholder="form.productIds.length === 1 ? '选择该商品下的规格' : '先选择一个商品'"
            style="width: 100%"
            data-testid="coupon-create-skus"
          >
            <el-option
              v-for="s in skuOptions"
              :key="String(s.skuId)"
              :label="`${s.spec || s.skuId} · ¥${(Number(s.price?.retailPriceCent ?? 0) / 100).toFixed(2)}`"
              :value="Number(s.skuId)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="有效期">
          <el-input-number v-model="form.validDays" :min="1" :max="365" style="width: 150px" />
          <span class="sent-hint">天，发放后按天计算到期</span>
        </el-form-item>

        <el-form-item label="放进领券中心">
          <el-switch v-model="form.claimable" data-testid="coupon-create-claimable" />
          <el-input-number
            v-if="form.claimable"
            v-model="form.totalQty"
            :min="-1"
            :max="999999"
            style="width: 150px; margin-left: 12px"
          />
          <span class="sent-hint">{{ form.claimable ? '张，-1 表示不限量' : '默认只在管理端发放' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" data-testid="coupon-create-submit" @click="submit">
          确认创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 管理端「客户与会员 → 优惠券创建」（2026-11-18 用户需求 2）。
 *
 * 运营在这里建「特定商品」的券：可限定 SPU（整件商品）或 SKU（某个规格/尺码），
 * 建好的券立刻出现在「优惠券管理 → 发放优惠券」的下拉里。
 *
 * 列表口径对齐发票管理：券号 / 创建人 / 创建时间 + 分页 + 批量删除 + 导出（导的是全量，
 * 不是当前页 —— 用户要的是「整张表下载下来」）。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { usePagination } from '@/composables/usePagination';
import { useBatchDelete } from '@/composables/useBatchDelete';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import type { TableColumn } from '@/components/types';
import {
  couponApi,
  type CouponTemplatePageVO,
  type CouponType,
} from '@/api/coupon';
import { productApi, type CatalogProductVO, type CatalogSkuVO } from '@/api/product';

const router = useRouter();

const TYPES: { value: CouponType; label: string }[] = [
  { value: 'NO_THRESHOLD', label: '无门槛券' },
  { value: 'FULL_REDUCTION', label: '满减券' },
  { value: 'CATEGORY', label: '品类券' },
  { value: 'ENTERPRISE', label: '企业专属券' },
];

/**
 * 列宽按 1440 视口内「一屏看全」定：券号/名称/面额/门槛/范围/库存/创建人/创建时间/状态 + 操作，
 * 合计 ≈1100px。10 列全展开会横向溢出，把创建时间挤到固定操作列下面（2026-11-18 截图核对发现），
 * 因此券型不再单占一列，改成券名后面的小标签（与发放记录页一致）。
 */
const columns: TableColumn[] = [
  { prop: 'templateNo', label: '券号', width: 130 },
  { prop: 'name', label: '券名称', minWidth: 170 },
  { prop: 'amountCent', label: '面额 / 折扣', width: 105, align: 'right',
    formatter: (row) => (row.discountType === 'PERCENT'
      ? rateText(Number(row.discountRate))
      : `¥${yuan(row.amountCent)}`) },
  { prop: 'thresholdCent', label: '门槛', width: 95, align: 'right',
    formatter: (_row, v) => thresholdText(v) },
  { prop: 'scopeType', label: '适用范围', width: 105 },
  { prop: 'claimedQty', label: '已领 / 库存', width: 100, align: 'right' },
  { prop: 'creatorName', label: '创建人', width: 100 },
  { prop: 'createdAtMillis', label: '创建时间', width: 150,
    formatter: (_row, v) => fmtDateTime(Number(v)) },
  { prop: 'status', label: '状态', width: 80, align: 'center' },
];

const rows = ref<CouponTemplatePageVO[]>([]);
const total = ref(0);
const loading = ref(false);
const keyword = ref('');
const couponType = ref<string>('');
const { page, size } = usePagination();
const { deleteMode, selected, onDeleted } = useBatchDelete(load);

function yuan(cent: number | string): string {
  const n = Number(cent ?? 0);
  return Number.isInteger(n / 100) ? String(n / 100) : (n / 100).toFixed(2);
}
function rateText(rate: number): string {
  const zhe = Number(rate ?? 100) / 10;
  return `${Number.isInteger(zhe) ? zhe : zhe.toFixed(1)}折`;
}
function typeText(type?: string): string {
  return TYPES.find((t) => t.value === type)?.label ?? '优惠券';
}
function thresholdText(cent: number | string): string {
  const n = Number(cent ?? 0);
  return n > 0 ? `满 ${yuan(n)} 元` : '无门槛';
}
function scopeText(row: CouponTemplatePageVO): string {
  if (row.scopeType === 'SPU') return '指定商品';
  if (row.scopeType === 'SKU') return '指定规格';
  return '全部商品';
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(couponApi.templatesPage({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      couponType: couponType.value || undefined,
    }));
    rows.value = data?.list ?? [];
    total.value = Number(data?.total ?? 0);
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '优惠券列表加载失败');
  } finally {
    loading.value = false;
  }
}

/** 导出「整张表」：按当前筛选条件把所有分页拉完（单页上限 100） */
async function fetchAllTemplates(): Promise<CouponTemplatePageVO[]> {
  const pageSize = 100;
  const all: CouponTemplatePageVO[] = [];
  for (let p = 1; p <= 50; p += 1) {
    const data = await unwrap(couponApi.templatesPage({
      page: p,
      size: pageSize,
      keyword: keyword.value.trim() || undefined,
      couponType: couponType.value || undefined,
    }));
    const list = data?.list ?? [];
    all.push(...list);
    if (list.length < pageSize || all.length >= Number(data?.total ?? 0)) break;
  }
  return all;
}

function onSearch(): void {
  page.value = 1;
  void load();
}
function onPage(value: number): void { page.value = value; void load(); }
function onSize(value: number): void { size.value = value; page.value = 1; void load(); }

/* ---------------- 新建 ---------------- */

const visible = ref(false);
const saving = ref(false);
const productLoading = ref(false);
const skuLoading = ref(false);
const productOptions = ref<CatalogProductVO[]>([]);
const skuOptions = ref<CatalogSkuVO[]>([]);

const form = reactive({
  name: '',
  couponType: 'CATEGORY' as CouponType,
  discountType: 'PERCENT' as 'AMOUNT' | 'PERCENT',
  amountYuan: 10,
  discountRate: 90,
  thresholdYuan: 0,
  scopeType: 'SPU' as 'ALL' | 'SPU' | 'SKU',
  productIds: [] as number[],
  skuIds: [] as number[],
  validDays: 30,
  claimable: false,
  totalQty: -1,
});

function openCreate(): void {
  visible.value = true;
  void searchProducts('');
}

function resetForm(): void {
  form.name = '';
  form.couponType = 'CATEGORY';
  form.discountType = 'PERCENT';
  form.amountYuan = 10;
  form.discountRate = 90;
  form.thresholdYuan = 0;
  form.scopeType = 'SPU';
  form.productIds = [];
  form.skuIds = [];
  form.validDays = 30;
  form.claimable = false;
  form.totalQty = -1;
  skuOptions.value = [];
}

async function searchProducts(keywordText: string): Promise<void> {
  productLoading.value = true;
  try {
    const data = await unwrap(productApi.catalog({
      page: 1,
      size: 20,
      keyword: keywordText.trim() || undefined,
    }));
    productOptions.value = data?.list ?? [];
  } catch {
    productOptions.value = [];
  } finally {
    productLoading.value = false;
  }
}

/** 指定规格：只允许先锁定一个商品，避免不同商品的 SKU 混在一张券里 */
watch(() => form.productIds.slice(), async (ids) => {
  form.skuIds = [];
  skuOptions.value = [];
  if (ids.length !== 1) return;
  skuLoading.value = true;
  try {
    skuOptions.value = (await unwrap(productApi.catalogSkus(ids[0]))) ?? [];
  } catch {
    skuOptions.value = [];
  } finally {
    skuLoading.value = false;
  }
});

const scopeIds = computed<string | null>(() => {
  if (form.scopeType === 'SPU') return form.productIds.length ? form.productIds.join(',') : null;
  if (form.scopeType === 'SKU') return form.skuIds.length ? form.skuIds.join(',') : null;
  return null;
});

async function submit(): Promise<void> {
  if (!form.name.trim()) {
    ElMessage.warning('请填写券名称');
    return;
  }
  if (form.discountType === 'AMOUNT' && !(form.amountYuan > 0)) {
    ElMessage.warning('请填写券面额');
    return;
  }
  if (form.scopeType !== 'ALL' && !scopeIds.value) {
    ElMessage.warning(form.scopeType === 'SPU' ? '请选择适用商品' : '请选择适用规格');
    return;
  }
  if ((form.couponType === 'FULL_REDUCTION' || form.couponType === 'ENTERPRISE')
    && !(form.thresholdYuan > 0)) {
    ElMessage.warning('满减券与企业券必须设置使用门槛');
    return;
  }

  saving.value = true;
  try {
    const created = await unwrap(couponApi.createTemplate({
      name: form.name.trim(),
      couponType: form.couponType,
      discountType: form.discountType,
      amountCent: form.discountType === 'AMOUNT' ? Math.round(form.amountYuan * 100) : 0,
      thresholdCent: form.couponType === 'NO_THRESHOLD' ? 0 : Math.round(form.thresholdYuan * 100),
      discountRate: form.discountType === 'PERCENT' ? form.discountRate : 100,
      scopeType: form.scopeType,
      scopeIds: scopeIds.value,
      customerScope: form.couponType === 'ENTERPRISE' ? 'ENTERPRISE' : 'ALL',
      validDays: form.validDays,
      claimable: form.claimable ? 1 : 0,
      totalQty: form.claimable ? form.totalQty : -1,
      status: 'ACTIVE',
    }));
    ElMessage.success(`创建成功，券号 ${created?.templateNo ?? ''}`);
    visible.value = false;
    page.value = 1;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '创建失败');
  } finally {
    saving.value = false;
  }
}

/** 建好就能直接去发放：带上模板 ID 跳到优惠券管理并预选 */
function goIssue(row: CouponTemplatePageVO): void {
  void router.push({ path: '/customer/coupon', query: { templateId: String(row.templateId) } });
}

onMounted(() => { void load(); });
</script>

<style scoped>
.list-alert { margin-bottom: 14px; }
.type-tag {
  display: inline-block;
  margin-left: 6px;
  font-size: 12px;
  color: var(--sent-primary);
  background: var(--sent-primary-soft, #eaf5f0);
  border-radius: 4px;
  padding: 1px 6px;
  white-space: nowrap;
  flex: none;
}
.create-form :deep(.el-form-item) { margin-bottom: 16px; }
</style>
