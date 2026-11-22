<template>
  <div class="fs-page">
    <div class="fs-head">
      <div>
        <h2 class="sent-page-title">秒杀活动</h2>
        <p class="sent-hint fs-sub">
          秒杀价只对<strong>已发布且处于活动时间窗内</strong>的规格生效：用户端详情页出现秒杀横幅、结算按秒杀价取价；
          没有活动时（或活动未开始 / 已结束 / 已下线）用户端看不到任何秒杀痕迹，按日常价销售。
        </p>
      </div>
      <div class="fs-head-actions">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" data-testid="flash-sale-create" @click="openDialog()">
          新建活动
        </el-button>
      </div>
    </div>

    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        row-key="activityId"
        :operation-width="196"
        exportable
        export-name="秒杀活动"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-input
            v-model="filter.keyword"
            placeholder="搜索活动标题或编号"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-select
            v-model="filter.status"
            placeholder="发布状态"
            clearable
            style="width: 130px"
            @change="onSearch"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="ACTIVE" />
            <el-option label="已下线" value="OFFLINE" />
          </el-select>
          <el-button @click="onSearch">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </template>

        <template #col-title="{ row }">
          <div class="fs-cell">
            <span class="fs-cell-main" :title="row.title">{{ row.title }}</span>
            <span class="fs-cell-sub">{{ row.activityNo }}</span>
          </div>
        </template>

        <template #col-window="{ row }">
          <div class="fs-cell">
            <span class="fs-cell-main">{{ fmtDateTime(row.startAt) }}</span>
            <span class="fs-cell-sub">至 {{ fmtDateTime(row.endAt) }}</span>
          </div>
        </template>

        <template #col-itemCount="{ row }">
          <span class="fs-count">{{ row.itemCount }} 个规格</span>
        </template>

        <template #col-status="{ row }">
          <StatusTag :label="statusText(row)" :tone="statusTone(row)" />
        </template>

        <template #operations="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button
            v-if="row.status === 'ACTIVE'"
            link
            type="warning"
            @click="toggleStatus(row, false)"
          >
            下线
          </el-button>
          <el-button v-else link type="success" @click="toggleStatus(row, true)">发布</el-button>
          <el-popconfirm title="确认删除该活动？删除后立即失效，但历史明细保留。" @confirm="remove(row)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>

        <template #empty>
          <div class="fs-empty">
            <el-empty description="" :image-size="72">
              <template #description>
                <p class="fs-empty-title">还没有秒杀活动</p>
                <p class="fs-empty-desc">
                  新建活动 → 选择规格、填一个低于零售价（且不低于底价）的秒杀价 → 发布，
                  到点用户端自动出现秒杀价。
                </p>
              </template>
              <el-button type="primary" @click="openDialog()">新建第一个活动</el-button>
            </el-empty>
          </div>
        </template>
      </SentTable>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑秒杀活动' : '新建秒杀活动'"
      width="960px"
      destroy-on-close
      top="6vh"
    >
      <el-form :model="form" label-width="92px">
        <el-form-item label="活动标题" required>
          <el-input
            v-model="form.title"
            maxlength="128"
            show-word-limit
            placeholder="例如：双十一限时秒杀"
            data-testid="flash-sale-title"
          />
        </el-form-item>

        <el-form-item label="活动时间" required>
          <el-date-picker
            v-model="form.range"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 100%"
            data-testid="flash-sale-range"
          />
          <p class="sent-hint fs-form-hint">
            时间窗决定用户端「秒杀」的展示区间；活动开始前只看得到日常价。
          </p>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>

        <el-form-item label="活动商品" required>
          <div class="fs-items">
            <div v-if="!items.length" class="fs-items-empty">
              还没有添加商品，点下面按钮按「商品 → 规格」挑选，一个活动最多 50 个规格。
            </div>

            <div v-for="(item, index) in items" :key="item.key" class="fs-item">
              <div class="fs-item-main">
                <template v-if="item.fixed">
                  <div class="fs-cell">
                    <span class="fs-cell-main">{{ item.name || `规格 #${item.skuId}` }}</span>
                    <span class="fs-cell-sub">{{ item.spec || item.skuCode }}</span>
                  </div>
                </template>
                <template v-else>
                  <el-select
                    v-model="item.productId"
                    placeholder="选择商品"
                    filterable
                    remote
                    :remote-method="searchProducts"
                    :loading="productLoading"
                    style="width: 240px"
                    @change="(value: string) => onProductChange(item, value)"
                  >
                    <el-option
                      v-for="p in productOptions"
                      :key="String(p.productId)"
                      :label="p.name"
                      :value="String(p.productId)"
                    />
                  </el-select>
                  <el-select
                    v-model="item.skuId"
                    placeholder="选择规格"
                    style="width: 200px"
                    :disabled="!item.productId"
                    :loading="item.loadingSkus"
                    @change="() => onSkuChange(item)"
                  >
                    <el-option
                      v-for="s in item.skuOptions"
                      :key="s.skuId"
                      :label="s.label"
                      :value="s.skuId"
                    />
                  </el-select>
                </template>
              </div>

              <div class="fs-item-price">
                <span class="fs-item-retail">
                  零售价 {{ money(item.retailPriceCent) }}
                  <template v-if="item.floorPriceCent > 0">
                    · 底价 {{ money(item.floorPriceCent) }}
                  </template>
                </span>
                <el-input-number
                  v-model="item.priceYuan"
                  :min="0"
                  :precision="2"
                  :step="1"
                  controls-position="right"
                  size="small"
                  style="width: 132px"
                />
                <span class="fs-item-unit">元</span>
                <span v-if="discountOf(item)" class="fs-item-discount">{{ discountOf(item) }} 折</span>
              </div>

              <p v-if="priceError(item)" class="fs-item-error">{{ priceError(item) }}</p>

              <el-button link type="danger" @click="removeItem(index)">移除</el-button>
            </div>

            <el-button class="fs-items-add" :disabled="items.length >= 50" @click="addItem">
              + 添加商品规格
            </el-button>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="fs-footer-hint">保存为草稿，确认无误后再发布；发布时校验规格上下架与时段冲突。</span>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" data-testid="flash-sale-save" @click="submit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 秒杀活动管理（V54）。
 *
 * 三条产品约束决定本页的形态：
 * 1. **秒杀价不是商品属性**：它是活动的产物，所以单独一个页面维护，
 *    商品编辑页不出现秒杀价输入框（避免运营以为改商品就能改秒杀价）。
 * 2. **发布才生效**：草稿/下线/过期都不影响售卖，用户端只在时间窗内看到秒杀价。
 * 3. **价格必须有上下界**：秒杀价 < 零售价且 ≥ 底价。前端先拦一遍给即时反馈，
 *    后端发布时再按同一口径复校（含 SKU 上架状态与时段重叠）。
 */
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtCent, fmtDateTime, yuanToCent } from '@sent/shared';
import type { StatusTone } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { productApi } from '@/api';
import type {
  CatalogProductVO,
  CatalogSkuVO,
  FlashSaleActivityVO,
  FlashSaleItem,
} from '@/api/product';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';
import { useTable } from '@/composables/useTable';

interface ProductOption {
  productId: string;
  name: string;
}

interface SkuChoice {
  skuId: string;
  label: string;
  retailPriceCent: number;
}

/** 编辑态下的一行活动商品；fixed=true 表示从后端回显、不允许再换规格（只能改价或移除）。 */
interface DraftItem {
  key: number;
  fixed: boolean;
  productId: string;
  skuId: string;
  skuCode: string;
  name: string;
  spec: string;
  retailPriceCent: number;
  floorPriceCent: number;
  priceYuan: number;
  skuOptions: SkuChoice[];
  loadingSkus: boolean;
}

const STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '已发布',
  PENDING: '待开始',
  ONGOING: '进行中',
  ENDED: '已结束',
  OFFLINE: '已下线',
};

const STATUS_TONE: Record<string, StatusTone> = {
  DRAFT: 'info',
  ACTIVE: 'primary',
  PENDING: 'warning',
  ONGOING: 'success',
  ENDED: 'info',
  OFFLINE: 'info',
};

const columns: TableColumn[] = [
  { prop: 'title', label: '活动', minWidth: 200 },
  { prop: 'window', label: '活动时间', minWidth: 168 },
  { prop: 'itemCount', label: '商品数', width: 104, align: 'center' },
  { prop: 'status', label: '状态', width: 96, align: 'center', formatter: (row: any) => statusText(row) },
];

const filter = reactive<{ keyword: string; status: string }>({ keyword: '', status: '' });

const { rows, total, loading, page, size, load, onPage, onSize, onSearch } =
  useTable<FlashSaleActivityVO, { keyword: string; status: string }>({
    fetch: (params) =>
      unwrap(
        productApi.flashSales({
          page: params.page,
          size: params.size,
          keyword: params.keyword || undefined,
          status: params.status || undefined,
        }),
      ),
    query: () => ({ keyword: filter.keyword.trim(), status: filter.status }),
    defaultSize: 20,
    paginationKey: 'product/flash-sale',
  });

const dialogVisible = ref(false);
const saving = ref(false);
const editingId = ref<string | null>(null);
const items = ref<DraftItem[]>([]);
const productOptions = ref<ProductOption[]>([]);
const productLoading = ref(false);
let rowSeed = 0;

const form = reactive({
  title: '',
  range: [] as string[],
  remark: '',
});

function money(cent?: number | null): string {
  return `¥${fmtCent(Number(cent ?? 0))}`;
}

function statusText(row: FlashSaleActivityVO): string {
  return STATUS_TEXT[row.displayStatus] ?? STATUS_TEXT[row.status] ?? '-';
}

function statusTone(row: FlashSaleActivityVO): StatusTone {
  return STATUS_TONE[row.displayStatus] ?? STATUS_TONE[row.status] ?? 'info';
}

/** 折扣只作参考展示：不打折（≥ 零售价）就不显示，避免出现「10.0 折」这种废话。 */
function discountOf(item: DraftItem): string | null {
  const price = yuanToCent(item.priceYuan);
  if (!price || !item.retailPriceCent) return null;
  const rate = (price * 100) / item.retailPriceCent;
  return rate > 0 && rate < 100 ? (rate / 10).toFixed(1) : null;
}

/** 与后端同口径的前端预校验：越界立刻红字提示，不必等保存失败。 */
function priceError(item: DraftItem): string {
  const price = yuanToCent(item.priceYuan);
  if (!item.skuId || !price) return '';
  if (item.retailPriceCent > 0 && price >= item.retailPriceCent) {
    return '秒杀价必须低于该规格零售价';
  }
  if (item.floorPriceCent > 0 && price < item.floorPriceCent) {
    return '秒杀价不能低于该规格底价';
  }
  return '';
}

function resetFilter(): void {
  filter.keyword = '';
  filter.status = '';
  onSearch();
}

function addItem(): void {
  items.value.push({
    key: ++rowSeed,
    fixed: false,
    productId: '',
    skuId: '',
    skuCode: '',
    name: '',
    spec: '',
    retailPriceCent: 0,
    floorPriceCent: 0,
    priceYuan: 0,
    skuOptions: [],
    loadingSkus: false,
  });
}

function removeItem(index: number): void {
  items.value.splice(index, 1);
}

/** 商品量可能上千，下拉走远程搜索，避免一次把全站商品塞进页面。 */
async function searchProducts(keyword: string): Promise<void> {
  productLoading.value = true;
  try {
    const pageData = await unwrap(
      productApi.catalog({ page: 1, size: 20, keyword: keyword || undefined }),
    );
    productOptions.value = (pageData?.list ?? []).map((p: CatalogProductVO) => ({
      productId: String(p.productId),
      name: p.name,
    }));
  } catch {
    productOptions.value = [];
  } finally {
    productLoading.value = false;
  }
}

async function onProductChange(item: DraftItem, productId: string): Promise<void> {
  item.skuId = '';
  item.skuCode = '';
  item.spec = '';
  item.priceYuan = 0;
  item.retailPriceCent = 0;
  item.floorPriceCent = 0;
  item.skuOptions = [];
  if (!productId) return;
  item.loadingSkus = true;
  try {
    const skus = await unwrap(productApi.catalogSkus(productId));
    item.skuOptions = (skus ?? [])
      .filter((s: CatalogSkuVO) => s.onShelf)
      .map((s: CatalogSkuVO) => ({
        skuId: String(s.skuId),
        label: `${s.spec || s.skuCode} · ${money(s.price?.retailPriceCent)}`,
        retailPriceCent: Number(s.price?.retailPriceCent ?? 0),
      }));
    if (!item.skuOptions.length) {
      ElMessage.warning('该商品没有可售规格，无法参加秒杀');
    }
  } catch (error) {
    ElMessage.error((error as Error)?.message || '规格加载失败');
  } finally {
    item.loadingSkus = false;
  }
}

function onSkuChange(item: DraftItem): void {
  const sku = item.skuOptions.find((s) => s.skuId === item.skuId);
  item.retailPriceCent = sku?.retailPriceCent ?? 0;
  item.spec = sku?.label?.split(' · ')[0] ?? '';
}

async function openDialog(row?: FlashSaleActivityVO): Promise<void> {
  editingId.value = row ? String(row.activityId) : null;
  form.title = row?.title ?? '';
  form.remark = row?.remark ?? '';
  form.range = row?.startAt && row?.endAt ? [row.startAt, row.endAt] : [];
  items.value = [];
  dialogVisible.value = true;
  void searchProducts('');

  if (!row) return;
  // 回显必须走详情：列表接口不带明细（否则一页活动会打出 N 次商品查询）
  try {
    const detail = await unwrap(productApi.flashSaleDetail(row.activityId));
    form.title = detail?.title ?? form.title;
    form.remark = detail?.remark ?? '';
    form.range = detail?.startAt && detail?.endAt ? [detail.startAt, detail.endAt] : form.range;
    items.value = (detail?.items ?? []).map((it) => ({
      key: ++rowSeed,
      fixed: true,
      productId: '',
      skuId: String(it.skuId),
      skuCode: it.skuCode ?? '',
      name: it.productName ?? '',
      spec: it.spec ?? '',
      retailPriceCent: Number(it.retailPriceCent ?? 0),
      floorPriceCent: Number(it.floorPriceCent ?? 0),
      priceYuan: Number(it.flashPriceCent ?? 0) / 100,
      skuOptions: [],
      loadingSkus: false,
    }));
  } catch (error) {
    ElMessage.error((error as Error)?.message || '活动明细加载失败');
  }
}

async function submit(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请填写活动标题');
    return;
  }
  const [startAt, endAt] = form.range;
  if (!startAt || !endAt) {
    ElMessage.warning('请选择活动起止时间');
    return;
  }
  if (!items.value.length) {
    ElMessage.warning('至少添加一个商品规格');
    return;
  }
  const payloadItems: FlashSaleItem[] = [];
  for (const item of items.value) {
    const priceCent = yuanToCent(item.priceYuan);
    if (!item.skuId) {
      ElMessage.warning('有商品行还没选择规格');
      return;
    }
    if (!priceCent || priceCent <= 0) {
      ElMessage.warning('请填写每个规格的秒杀价');
      return;
    }
    const error = priceError(item);
    if (error) {
      ElMessage.warning(error);
      return;
    }
    payloadItems.push({ skuId: Number(item.skuId), flashPriceCent: priceCent });
  }

  saving.value = true;
  try {
    const body = { title: form.title.trim(), startAt, endAt, remark: form.remark, items: payloadItems };
    if (editingId.value) {
      await unwrap(productApi.updateFlashSale(editingId.value, body));
      ElMessage.success('活动已保存');
    } else {
      await unwrap(productApi.createFlashSale(body));
      ElMessage.success('活动已创建，发布后生效');
    }
    dialogVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(row: FlashSaleActivityVO, active: boolean): Promise<void> {
  try {
    await unwrap(productApi.setFlashSaleStatus(row.activityId, active));
    ElMessage.success(active ? '已发布，到点自动生效' : '已下线，用户端立即恢复日常价');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '操作失败');
  }
}

async function remove(row: FlashSaleActivityVO): Promise<void> {
  try {
    await unwrap(productApi.removeFlashSale(row.activityId));
    ElMessage.success('已删除');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '删除失败');
  }
}
</script>

<style scoped>
.fs-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.fs-sub {
  margin: -8px 0 14px;
  max-width: 820px;
  line-height: 1.7;
}
.fs-sub strong {
  color: var(--sent-ink);
  font-weight: 600;
}
.fs-head-actions {
  display: flex;
  gap: 8px;
  padding-top: 2px;
}
.fs-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
  min-width: 0;
}
.fs-cell-main {
  color: var(--sent-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fs-cell-sub {
  font-size: 12px;
  color: var(--sent-ink-3);
}
.fs-count {
  color: var(--sent-ink-2);
}
.fs-empty {
  padding: 26px 0 34px;
}
.fs-empty-title {
  margin: 0 0 6px;
  font-size: 15px;
  color: var(--sent-ink);
}
.fs-empty-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--sent-ink-3);
  line-height: 1.7;
}
.fs-form-hint {
  margin: 6px 0 0;
}
.fs-items {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.fs-items-empty {
  font-size: 13px;
  color: var(--sent-ink-3);
}
.fs-items-add {
  align-self: flex-start;
}
.fs-item {
  border: 1px solid var(--sent-line);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--sent-surface);
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 8px 14px;
  align-items: center;
}
.fs-item-main {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}
.fs-item-price {
  display: flex;
  align-items: center;
  gap: 8px;
}
.fs-item-retail {
  font-size: 12px;
  color: var(--sent-ink-3);
  white-space: nowrap;
}
.fs-item-unit {
  font-size: 12px;
  color: var(--sent-ink-3);
}
.fs-item-discount {
  font-size: 12px;
  color: var(--sent-accent-ink);
  background: var(--sent-accent-soft);
  padding: 2px 8px;
  border-radius: 10px;
}
.fs-item-error {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 12px;
  color: var(--sent-danger);
}
.fs-footer-hint {
  float: left;
  font-size: 12px;
  color: var(--sent-ink-3);
  line-height: 32px;
}
</style>
